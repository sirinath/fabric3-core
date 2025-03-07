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
package org.fabric3.implementation.pojo.generator;

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.fabric3.api.model.type.java.Injectable;
import org.fabric3.api.model.type.java.InjectingComponentType;
import org.fabric3.api.model.type.java.InjectionSite;
import org.fabric3.implementation.pojo.provision.ImplementationManagerDefinition;
import org.fabric3.implementation.pojo.provision.PhysicalPojoComponent;
import org.fabric3.spi.model.instance.LogicalComponent;
import org.fabric3.spi.model.instance.LogicalProperty;
import org.fabric3.spi.model.physical.PhysicalProperty;
import org.fabric3.spi.model.type.java.ConstructorInjectionSite;
import org.w3c.dom.Document;

/**
 *
 */
public class GenerationHelperImpl implements GenerationHelper {

    public void processInjectionSites(InjectingComponentType componentType, ImplementationManagerDefinition managerDefinition) {

        Map<InjectionSite, Injectable> mappings = componentType.getInjectionSites();

        // add injections for all the active constructor args
        Map<InjectionSite, Injectable> construction = managerDefinition.getConstruction();
        Constructor<?> constructor = componentType.getConstructor();
        Set<Injectable> byConstruction = new HashSet<>(constructor.getParameterTypes().length);
        for (int i = 0; i < constructor.getParameterTypes().length; i++) {
            InjectionSite site = new ConstructorInjectionSite(constructor, i);
            Injectable attribute = mappings.get(site);
            construction.put(site, attribute);
            byConstruction.add(attribute);
        }

        // add field/method injections
        Map<InjectionSite, Injectable> postConstruction = managerDefinition.getPostConstruction();
        Map<InjectionSite, Injectable> reinjection = managerDefinition.getReinjectables();
        for (Map.Entry<InjectionSite, Injectable> entry : mappings.entrySet()) {
            InjectionSite site = entry.getKey();
            if (site instanceof ConstructorInjectionSite) {
                continue;
            }

            Injectable attribute = entry.getValue();
            if (!byConstruction.contains(attribute)) {
                postConstruction.put(site, attribute);
            }
            reinjection.put(site, attribute);
        }
    }

    public void processPropertyValues(LogicalComponent<?> component, PhysicalPojoComponent pojoComponent) {
        for (LogicalProperty property : component.getAllProperties().values()) {
            String name = property.getName();
            boolean many = property.isMany();
            boolean required = property.isRequired();
            if (property.getKey() != null) {
                PhysicalProperty physicalProperty = PhysicalProperty.Builder.newBuilder(name).key(property.getKey()).required(required).many(many).build();
                pojoComponent.setProperty(physicalProperty);
            } else if (property.getXmlValue() != null) {
                Document document = property.getXmlValue();
                PhysicalProperty physicalProperty = PhysicalProperty.Builder.newBuilder(name).xmlValue(document).required(required).many(many).build();
                pojoComponent.setProperty(physicalProperty);
            } else if (property.getInstanceValue() != null) {
                Object value = property.getInstanceValue();
                PhysicalProperty physicalProperty = PhysicalProperty.Builder.newBuilder(name).instanceValue(value).required(required).many(many).build();
                pojoComponent.setProperty(physicalProperty);
            }
        }
    }
}
