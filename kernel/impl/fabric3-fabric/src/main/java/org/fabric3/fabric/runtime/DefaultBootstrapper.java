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
 *
 * Portions originally based on Apache Tuscany 2007
 * licensed under the Apache 2.0 license.
 */
package org.fabric3.fabric.runtime;

import javax.management.MBeanServer;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.fabric3.api.host.Fabric3Exception;
import org.fabric3.api.host.Version;
import org.fabric3.api.host.domain.Domain;
import org.fabric3.api.host.monitor.DestinationRouter;
import org.fabric3.api.host.monitor.MonitorProxyService;
import org.fabric3.api.host.monitor.Monitorable;
import org.fabric3.api.host.runtime.BootConfiguration;
import org.fabric3.api.host.runtime.BootExports;
import org.fabric3.api.host.runtime.ComponentRegistration;
import org.fabric3.api.host.runtime.Fabric3Runtime;
import org.fabric3.api.host.runtime.HostInfo;
import org.fabric3.api.model.type.component.Composite;
import org.fabric3.contribution.manifest.ContributionExport;
import org.fabric3.fabric.container.channel.ChannelManager;
import org.fabric3.fabric.domain.LogicalComponentManager;
import org.fabric3.fabric.domain.instantiator.component.AtomicComponentInstantiatorImpl;
import org.fabric3.fabric.runtime.bootstrap.BootstrapAssemblyFactory;
import org.fabric3.fabric.runtime.bootstrap.BootstrapCompositeFactory;
import org.fabric3.fabric.runtime.bootstrap.BootstrapIntrospectionFactory;
import org.fabric3.fabric.runtime.bootstrap.Java6HostExports;
import org.fabric3.fabric.synthesizer.ComponentSynthesizer;
import org.fabric3.fabric.synthesizer.SingletonComponentSynthesizer;
import org.fabric3.introspection.java.DefaultIntrospectionHelper;
import org.fabric3.introspection.java.contract.JavaContractProcessorImpl;
import org.fabric3.spi.classloader.ClassLoaderRegistry;
import org.fabric3.spi.container.component.ComponentManager;
import org.fabric3.spi.container.component.ScopeContainer;
import org.fabric3.spi.container.component.ScopeRegistry;
import org.fabric3.spi.contribution.Capability;
import org.fabric3.spi.contribution.Contribution;
import org.fabric3.spi.contribution.ContributionManifest;
import org.fabric3.spi.contribution.MetaDataStore;
import org.fabric3.spi.contribution.manifest.JavaExport;
import org.fabric3.spi.contribution.manifest.PackageInfo;
import org.fabric3.spi.introspection.java.ImplementationIntrospector;
import org.fabric3.spi.introspection.java.IntrospectionHelper;
import org.fabric3.spi.management.ManagementService;
import org.fabric3.spi.model.instance.LogicalCompositeComponent;
import org.fabric3.spi.model.instance.LogicalProperty;
import org.w3c.dom.Document;
import static org.fabric3.api.host.Names.BOOT_CONTRIBUTION;
import static org.fabric3.api.host.Names.HOST_CONTRIBUTION;

/**
 * The default Bootstrapper implementation.
 */
public class DefaultBootstrapper implements Bootstrapper {
    private static final URI RUNTIME_SERVICES = URI.create("fabric3://RuntimeServices");

    // bootstrap components - these are disposed of after the core runtime system components are booted
    private JavaContractProcessorImpl contractProcessor;
    private AtomicComponentInstantiatorImpl instantiator;
    private ImplementationIntrospector implementationIntrospector;
    private ComponentSynthesizer synthesizer;

    // runtime components - these are persistent and supplied by the runtime implementation
    private MonitorProxyService monitorService;
    private ClassLoaderRegistry classLoaderRegistry;
    private MetaDataStore metaDataStore;
    private ScopeRegistry scopeRegistry;
    private LogicalCompositeComponent domain;
    private LogicalComponentManager lcm;
    private ComponentManager componentManager;
    private ChannelManager channelManager;
    private ScopeContainer scopeContainer;
    private MBeanServer mbeanServer;
    private ManagementService managementService;
    private HostInfo hostInfo;

    private Domain runtimeDomain;

    private Fabric3Runtime runtime;
    private Document systemConfig;
    private ClassLoader bootClassLoader;
    private Map<String, String> exportedPackages;
    private List<String> hostCapabilities;
    private ClassLoader hostClassLoader;
    private Contribution bootContribution;
    private List<ComponentRegistration> registrations;
    private DestinationRouter router;

    public DefaultBootstrapper(BootConfiguration configuration) {
        runtime = configuration.getRuntime();
        systemConfig = configuration.getSystemConfig();
        hostClassLoader = configuration.getHostClassLoader();
        bootClassLoader = configuration.getBootClassLoader();
        exportedPackages = configuration.getExportedPackages();
        hostCapabilities = configuration.getHostCapabilities();
        registrations = configuration.getRegistrations();

        // create disposable components needed to bootstrap the runtime
        IntrospectionHelper helper = new DefaultIntrospectionHelper();
        contractProcessor = new JavaContractProcessorImpl(helper);
        instantiator = new AtomicComponentInstantiatorImpl();
        implementationIntrospector = BootstrapIntrospectionFactory.createSystemImplementationProcessor();
    }

    public void bootRuntimeDomain() throws Fabric3Exception {
        RuntimeServices runtimeServices = runtime.getComponent(RuntimeServices.class, RUNTIME_SERVICES);
        hostInfo = runtimeServices.getHostInfo();
        monitorService = runtimeServices.getMonitorProxyService();
        router = runtimeServices.getDestinationRouter();
        lcm = runtimeServices.getLogicalComponentManager();
        componentManager = runtimeServices.getComponentManager();
        channelManager = runtimeServices.getChannelManager();
        domain = lcm.getRootComponent();
        classLoaderRegistry = runtimeServices.getClassLoaderRegistry();
        metaDataStore = runtimeServices.getMetaDataStore();
        scopeRegistry = runtimeServices.getScopeRegistry();
        scopeContainer = runtimeServices.getScopeContainer();
        mbeanServer = runtimeServices.getMBeanServer();
        managementService = runtimeServices.getManagementService();
        hostInfo = runtimeServices.getHostInfo();

        synthesizer = new SingletonComponentSynthesizer(implementationIntrospector, instantiator, lcm, componentManager, contractProcessor, scopeContainer);

        // register components provided by the runtime itself so they may be wired to
        registerRuntimeComponents(registrations);

        runtimeDomain = BootstrapAssemblyFactory.createDomain(monitorService,
                                                              classLoaderRegistry,
                                                              scopeRegistry,
                                                              componentManager,
                                                              lcm,
                                                              metaDataStore,
                                                              managementService,
                                                              hostInfo);

        // register the runtime domain component
        registerComponent("RuntimeDomain", Domain.class, runtimeDomain, true);

        // create host and boot contributions
        synthesizeContributions();
    }

    public void bootSystem() throws Fabric3Exception {
        // load the system composite
        Composite composite = BootstrapCompositeFactory.createSystemComposite(bootContribution, hostInfo, bootClassLoader, implementationIntrospector);

        // create the property and merge it into the composite
        LogicalProperty logicalProperty = LogicalProperty.Builder.newBuilder("systemConfig", domain).xmlValue(systemConfig).many(false).build();
        domain.setProperties(logicalProperty);

        // deploy the composite to the runtime domain
        runtimeDomain.include(composite);
    }

    /**
     * Registers the primordial runtime components.
     *
     * @param registrations host components to register
     * @throws Fabric3Exception if there is an error during registration
     */
    @SuppressWarnings({"unchecked"})
    private <S, I extends S> void registerRuntimeComponents(List<ComponentRegistration> registrations) throws Fabric3Exception {

        // services available through the outward facing Fabric3Runtime API
        registerComponent("MonitorProxyService", MonitorProxyService.class, monitorService, true);
        registerComponent("DestinationRouter", DestinationRouter.class, router, true);

        Class<HostInfo> type = getHostInfoType(hostInfo);
        registerComponent("HostInfo", type, hostInfo, true);
        if (mbeanServer != null) {
            registerComponent("MBeanServer", MBeanServer.class, mbeanServer, false);
        }
        registerComponent("ManagementService", ManagementService.class, managementService, true);

        // services available through the inward facing RuntimeServices SPI
        registerComponent("ComponentManager", ComponentManager.class, componentManager, true);
        registerComponent("ChannelManager", ChannelManager.class, channelManager, true);
        registerComponent("RuntimeLogicalComponentManager", LogicalComponentManager.class, lcm, true);
        registerComponent("CompositeScopeContainer", ScopeContainer.class, scopeContainer, true);
        registerComponent("ClassLoaderRegistry", ClassLoaderRegistry.class, classLoaderRegistry, true);
        registerComponent("ScopeRegistry", ScopeRegistry.class, scopeRegistry, true);
        registerComponent("MetaDataStore", MetaDataStore.class, metaDataStore, true);
        registerComponent("Monitorable", Monitorable.class, runtime, false);

        // register other components provided by the host environment
        for (ComponentRegistration registration : registrations) {
            String name = registration.getName();
            Class<S> service = (Class<S>) registration.getService();
            I instance = (I) registration.getInstance();
            boolean introspect = registration.isIntrospect();
            registerComponent(name, service, instance, introspect);
        }
    }

    /**
     * Determines the specific HostInfo interface subtype to register the HostInfo instance with.
     *
     * @param info the HostInfo
     * @return the interface to register the HostInfo instance with
     */
    @SuppressWarnings({"unchecked"})
    private Class<HostInfo> getHostInfoType(HostInfo info) {
        Class<?>[] interfaces = info.getClass().getInterfaces();
        if (interfaces.length == 1) {
            return HostInfo.class;
        }
        for (Class<?> interfaze : interfaces) {
            if (!HostInfo.class.equals(interfaze) && HostInfo.class.isAssignableFrom(interfaze)) {
                return (Class<HostInfo>) interfaze;
            }
        }
        return HostInfo.class;
    }

    /**
     * Registers a primordial component.
     *
     * @param name       the component named
     * @param type       the service interface type
     * @param instance   the component instance
     * @param introspect true if the component should be introspected for references
     * @throws Fabric3Exception if there is an error during registration
     */
    private <S, I extends S> void registerComponent(String name, Class<S> type, I instance, boolean introspect) throws Fabric3Exception {
        synthesizer.registerComponent(name, type, instance, introspect);
    }

    /**
     * Creates contributions for the host and boot classloaders. These contributions may be imported by extensions and user contributions.
     *
     * @throws Fabric3Exception if there is an error synthesizing the contributions
     */
    private void synthesizeContributions() throws Fabric3Exception {
        // export packages included in JDK 6
        synthesizeContribution(HOST_CONTRIBUTION, Java6HostExports.getExports(), hostCapabilities, hostClassLoader, true);
        // add default boot exports
        exportedPackages.putAll(BootExports.getExports());
        bootContribution = synthesizeContribution(BOOT_CONTRIBUTION, exportedPackages, Collections.<String>emptyList(), bootClassLoader, true);
    }

    /**
     * Synthesizes a contribution from a classloader and installs it.
     *
     * @param contributionUri  the contribution URI
     * @param exportedPackages the packages exported by the contribution
     * @param hostCapabilities the capabilities provided by the contribution
     * @param loader           the classloader
     * @param extension        true if the contribution is an extension
     * @return the synthesized contribution
     * @throws Fabric3Exception if there is an error synthesizing the contribution
     */
    private Contribution synthesizeContribution(URI contributionUri,
                                                Map<String, String> exportedPackages,
                                                List<String> hostCapabilities,
                                                ClassLoader loader,
                                                boolean extension) throws Fabric3Exception {
        Contribution contribution = new Contribution(contributionUri);
        contribution.install();
        ContributionManifest manifest = contribution.getManifest();
        manifest.setExtension(extension);
        // add the ContributionExport
        manifest.addExport(new ContributionExport(contributionUri));
        for (Map.Entry<String, String> entry : exportedPackages.entrySet()) {
            Version version = new Version(entry.getValue());
            PackageInfo info = new PackageInfo(entry.getKey(), version);
            JavaExport export = new JavaExport(info);
            manifest.addExport(export);
        }
        for (String capability : hostCapabilities) {
            manifest.addProvidedCapability(new Capability(capability));
        }
        metaDataStore.store(contribution);
        classLoaderRegistry.register(contributionUri, loader);
        return contribution;
    }

}