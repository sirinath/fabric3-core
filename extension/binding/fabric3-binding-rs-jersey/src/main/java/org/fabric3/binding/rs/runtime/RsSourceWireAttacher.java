/*
 * Fabric3
 * Copyright (c) 2009-2015 Metaform Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * Portions originally based on Apache Tuscany 2007
 * licensed under the Apache 2.0 license.
 */
package org.fabric3.binding.rs.runtime;

import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.fabric3.api.annotation.Source;
import org.fabric3.api.annotation.monitor.Monitor;
import org.fabric3.api.annotation.wire.Key;
import org.fabric3.api.host.Fabric3Exception;
import org.fabric3.binding.rs.provision.RsWireSource;
import org.fabric3.binding.rs.runtime.container.F3ResourceHandler;
import org.fabric3.binding.rs.runtime.container.RsContainer;
import org.fabric3.binding.rs.runtime.container.RsContainerManager;
import org.fabric3.binding.rs.runtime.provider.NameBindingFilterProvider;
import org.fabric3.binding.rs.runtime.provider.ProviderRegistry;
import org.fabric3.spi.container.builder.SourceWireAttacher;
import org.fabric3.spi.container.wire.InvocationChain;
import org.fabric3.spi.container.wire.Wire;
import org.fabric3.spi.host.ServletHost;
import org.fabric3.spi.introspection.java.AnnotationHelper;
import org.fabric3.spi.model.physical.PhysicalOperation;
import org.fabric3.spi.model.physical.PhysicalWireTarget;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.oasisopen.sca.annotation.EagerInit;
import org.oasisopen.sca.annotation.Property;
import org.oasisopen.sca.annotation.Reference;

/**
 *
 */
@EagerInit
@Key("org.fabric3.binding.rs.provision.RsWireSource")
public class RsSourceWireAttacher implements SourceWireAttacher<RsWireSource> {
    private static final String[] EMPTY_ARRAY = new String[0];
    private ServletHost servletHost;
    private RsContainerManager containerManager;
    private ProviderRegistry providerRegistry;
    private NameBindingFilterProvider provider;
    private RsWireAttacherMonitor monitor;
    private Level logLevel = Level.WARNING;

    public RsSourceWireAttacher(@Reference ServletHost servletHost,
                                @Reference RsContainerManager containerManager,
                                @Reference ProviderRegistry providerRegistry,
                                @Reference NameBindingFilterProvider provider,
                                @Monitor RsWireAttacherMonitor monitor) {
        this.servletHost = servletHost;
        this.containerManager = containerManager;
        this.providerRegistry = providerRegistry;
        this.provider = provider;
        this.monitor = monitor;
        setDebugLevel();
    }

    @Property(required = false)
    @Source("$systemConfig/f3:binding.rs/@log.level")
    public void setLogLevel(String level) {
        this.logLevel = Level.parse(level);
    }

    public void attach(RsWireSource source, PhysicalWireTarget target, Wire wire) throws Fabric3Exception {
        URI sourceUri = source.getUri();
        RsContainer container = containerManager.get(sourceUri);
        if (container == null) {
            // each resource defined with the same binding URI will be deployed to the same container
            container = new RsContainer(sourceUri.toString(), providerRegistry, provider);
            containerManager.register(sourceUri, container);
            String mapping = creatingMappingUri(sourceUri);
            if (servletHost.isMappingRegistered(mapping)) {
                // wire reprovisioned
                servletHost.unregisterMapping(mapping);
            }
            servletHost.registerMapping(mapping, container);
        }

        provision(source, wire, container);
        Path pathAnnotation = source.getRsClass().getAnnotation(Path.class);
        String uri = sourceUri.toString();
        if (pathAnnotation != null && !pathAnnotation.value().equals("/")) {
            String endpointUri = concat(uri, pathAnnotation);
            monitor.provisionedEndpoint(endpointUri);
        } else {
            monitor.provisionedEndpoint(uri);
        }
    }

    private String concat(String uri, Path pathAnnotation) {
        String path = pathAnnotation.value();
        if (uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 2);
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return uri + "/" + path;
    }

    public void detach(RsWireSource source, PhysicalWireTarget target) {
        URI sourceUri = source.getUri();
        String mapping = creatingMappingUri(sourceUri);
        servletHost.unregisterMapping(mapping);
        containerManager.unregister(sourceUri);
        Path pathAnnotation = source.getRsClass().getAnnotation(Path.class);
        String uri = sourceUri.toString();
        if (pathAnnotation != null && !pathAnnotation.value().equals("/")) {
            String endpointUri = concat(uri, pathAnnotation);
            monitor.removedEndpoint(endpointUri);
        } else {
            monitor.removedEndpoint(uri);
        }
    }

    private String creatingMappingUri(URI sourceUri) {
        String servletMapping = sourceUri.getPath();
        if (!servletMapping.endsWith("/*")) {
            servletMapping = servletMapping + "/*";
        }
        return servletMapping;
    }

    private void provision(RsWireSource source, Wire wire, RsContainer container) {
        Map<String, InvocationChain> invocationChains = new HashMap<>();
        for (InvocationChain chain : wire.getInvocationChains()) {
            PhysicalOperation operation = chain.getPhysicalOperation();
            invocationChains.put(operation.getName(), chain);
        }

        Class<?> interfaze = source.getRsClass();
        F3ResourceHandler handler = new F3ResourceHandler(interfaze, invocationChains);

        // Set the class loader to the runtime one so Jersey loads the Resource config properly
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            Resource resource = createResource(handler);
            container.addResource(resource);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    private Resource createResource(F3ResourceHandler handler) {
        Class<?> interfaze = handler.getInterface();

        Resource template = Resource.from(interfaze);
        if (template == null) {
            throw new Fabric3Exception("Interface is not a JAX-RS resource: " + interfaze.getName());
        }

        // introspect consumes and produces annotations on the JAX-RS type as well as in meta-annotations
        Consumes consumes = AnnotationHelper.findAnnotation(Consumes.class, interfaze);
        String[] consumeTypes = consumes != null ? consumes.value() : EMPTY_ARRAY;

        Produces produces = AnnotationHelper.findAnnotation(Produces.class, interfaze);
        String[] produceTypes = produces != null ? produces.value() : EMPTY_ARRAY;

        Resource.Builder resourceBuilder = Resource.builder(template.getPath());
        for (ResourceMethod resourceMethod : template.getAllMethods()) {
            createMethod(resourceBuilder, resourceMethod, handler, consumeTypes, produceTypes);
        }
        for (Resource childTemplate : template.getChildResources()) {
            Resource.Builder childResourceBuilder = Resource.builder(childTemplate.getPath());
            for (ResourceMethod resourceMethod : childTemplate.getAllMethods()) {
                createMethod(childResourceBuilder, resourceMethod, handler, consumeTypes, produceTypes);
            }
            resourceBuilder.addChildResource(childResourceBuilder.build());
        }
        return resourceBuilder.build();
    }

    private void createMethod(Resource.Builder resourceBuilder,
                              ResourceMethod template,
                              F3ResourceHandler handler,
                              String[] consumeTypes,
                              String[] produceTypes) {
        ResourceMethod.Builder methodBuilder = resourceBuilder.addMethod(template.getHttpMethod());
        methodBuilder.consumes(template.getConsumedTypes());
        methodBuilder.consumes(consumeTypes);
        methodBuilder.produces(template.getProducedTypes());
        methodBuilder.produces(produceTypes);
        methodBuilder.handledBy(handler, template.getInvocable().getHandlingMethod());
        if (template.isSuspendDeclared()) {
            methodBuilder.suspended(template.getSuspendTimeout(), template.getSuspendTimeoutUnit());
        }
        if (template.isManagedAsyncDeclared()) {
            methodBuilder.managedAsync();
        }
    }

    private void setDebugLevel() {
        Logger logger = Logger.getLogger("org.glassfish.jersey.");
        logger.setLevel(logLevel);
    }

}
