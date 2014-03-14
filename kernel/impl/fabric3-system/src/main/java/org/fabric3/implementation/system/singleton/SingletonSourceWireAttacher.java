/*
 * Fabric3
 * Copyright (c) 2009-2013 Metaform Systems
 *
 * Fabric3 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version, with the
 * following exception:
 *
 * Linking this software statically or dynamically with other
 * modules is making a combined work based on this software.
 * Thus, the terms and conditions of the GNU General Public
 * License cover the whole combination.
 *
 * As a special exception, the copyright holders of this software
 * give you permission to link this software with independent
 * modules to produce an executable, regardless of the license
 * terms of these independent modules, and to copy and distribute
 * the resulting executable under terms of your choice, provided
 * that you also meet, for each linked independent module, the
 * terms and conditions of the license of that module. An
 * independent module is a module which is not derived from or
 * based on this software. If you modify this software, you may
 * extend this exception to your version of the software, but
 * you are not obligated to do so. If you do not wish to do so,
 * delete this exception statement from your version.
 *
 * Fabric3 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the
 * GNU General Public License along with Fabric3.
 * If not, see <http://www.gnu.org/licenses/>.
 *
 * ----------------------------------------------------
 *
 * Portions originally based on Apache Tuscany 2007
 * licensed under the Apache 2.0 license.
 *
 */
package org.fabric3.implementation.system.singleton;

import java.net.URI;

import org.oasisopen.sca.annotation.EagerInit;
import org.oasisopen.sca.annotation.Reference;

import org.fabric3.spi.container.builder.WiringException;
import org.fabric3.spi.container.builder.component.SourceWireAttacher;
import org.fabric3.spi.container.component.ComponentManager;
import org.fabric3.spi.model.physical.PhysicalWireTargetDefinition;
import org.fabric3.api.model.type.java.Injectable;
import org.fabric3.spi.container.objectfactory.InjectionAttributes;
import org.fabric3.spi.container.objectfactory.ObjectFactory;
import org.fabric3.spi.util.UriHelper;
import org.fabric3.spi.container.wire.Wire;

/**
 * Reinjects singleton components after the runtime bootstrap.
 */
@EagerInit
public class SingletonSourceWireAttacher implements SourceWireAttacher<SingletonWireSourceDefinition> {
    private ComponentManager manager;

    public SingletonSourceWireAttacher(@Reference ComponentManager manager) {
        this.manager = manager;
    }

    public void attach(SingletonWireSourceDefinition source, PhysicalWireTargetDefinition target, Wire wire) throws WiringException {
        throw new UnsupportedOperationException();
    }

    public void detach(SingletonWireSourceDefinition source, PhysicalWireTargetDefinition target) throws WiringException {
    }

    public void detachObjectFactory(SingletonWireSourceDefinition source, PhysicalWireTargetDefinition target) throws WiringException {
        URI sourceName = UriHelper.getDefragmentedName(source.getUri());
        SingletonComponent component = (SingletonComponent) manager.getComponent(sourceName);
        Injectable injectable = source.getInjectable();
        component.removeObjectFactory(injectable);
    }

    public void attachObjectFactory(SingletonWireSourceDefinition source, ObjectFactory<?> objectFactory, PhysicalWireTargetDefinition target)
            throws WiringException {
        URI sourceId = UriHelper.getDefragmentedName(source.getUri());
        SingletonComponent sourceComponent = (SingletonComponent) manager.getComponent(sourceId);
        Injectable injectable = source.getInjectable();
        // Add the object factory for the target to be reinjected.
        // The Injectable identifies the injection site (a field or method) on the singleton instance.
        String key = source.getKey();
        int order = source.getOrder();
        InjectionAttributes attributes = new InjectionAttributes(key, order);
        sourceComponent.addObjectFactory(injectable, objectFactory, attributes);
    }
}