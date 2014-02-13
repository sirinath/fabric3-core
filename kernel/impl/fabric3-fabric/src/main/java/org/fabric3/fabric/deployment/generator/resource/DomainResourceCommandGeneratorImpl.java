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
*/
package org.fabric3.fabric.deployment.generator.resource;

import java.util.ArrayList;
import java.util.List;

import org.oasisopen.sca.annotation.Reference;

import org.fabric3.fabric.deployment.command.BuildResourcesCommand;
import org.fabric3.fabric.deployment.command.DisposeResourcesCommand;
import org.fabric3.fabric.deployment.generator.GeneratorRegistry;
import org.fabric3.api.model.type.component.ResourceDefinition;
import org.fabric3.spi.command.CompensatableCommand;
import org.fabric3.spi.deployment.generator.GenerationException;
import org.fabric3.spi.deployment.generator.resource.ResourceGenerator;
import org.fabric3.spi.model.instance.LogicalResource;
import org.fabric3.spi.model.instance.LogicalState;
import org.fabric3.spi.model.physical.PhysicalResourceDefinition;

/**
 *
 */
public class DomainResourceCommandGeneratorImpl implements DomainResourceCommandGenerator {
    private GeneratorRegistry generatorRegistry;

    public DomainResourceCommandGeneratorImpl(@Reference GeneratorRegistry generatorRegistry) {
        this.generatorRegistry = generatorRegistry;
    }

    public CompensatableCommand generateBuild(LogicalResource resource, boolean incremental) throws GenerationException {
        if (resource.getState() != LogicalState.NEW && incremental) {
            return null;
        }

        List<PhysicalResourceDefinition> definitions = createDefinitions(resource);
        if (definitions.isEmpty()) {
            return null;
        }
        return new BuildResourcesCommand(definitions);
    }

    public CompensatableCommand generateDispose(LogicalResource resource, boolean incremental) throws GenerationException {
        if (resource.getState() != LogicalState.MARKED) {
            return null;
        }
        List<PhysicalResourceDefinition> definitions = createDefinitions(resource);
        if (definitions.isEmpty()) {
            return null;
        }
        return new DisposeResourcesCommand(definitions);
    }

    @SuppressWarnings({"unchecked"})
    private List<PhysicalResourceDefinition> createDefinitions(LogicalResource resource) throws GenerationException {
        List<PhysicalResourceDefinition> definitions = new ArrayList<>();
        ResourceDefinition resourceDefinition = resource.getDefinition();
        ResourceGenerator generator = generatorRegistry.getResourceGenerator(resourceDefinition.getClass());
        PhysicalResourceDefinition definition = generator.generateResource(resource);
        definitions.add(definition);
        return definitions;
    }


}