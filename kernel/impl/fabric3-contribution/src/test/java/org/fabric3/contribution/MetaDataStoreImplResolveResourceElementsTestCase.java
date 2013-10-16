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
package org.fabric3.contribution;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;

import junit.framework.TestCase;

import org.fabric3.contribution.wire.ContributionWireInstantiator;
import org.fabric3.contribution.wire.ContributionWireInstantiatorRegistryImpl;
import org.fabric3.contribution.wire.QNameWireInstantiator;
import org.fabric3.api.host.util.FileHelper;
import org.fabric3.api.model.type.component.Composite;
import org.fabric3.spi.contribution.Contribution;
import org.fabric3.spi.contribution.ContributionManifest;
import org.fabric3.spi.contribution.ContributionWire;
import org.fabric3.spi.contribution.Import;
import org.fabric3.spi.contribution.Resource;
import org.fabric3.spi.contribution.ResourceElement;
import org.fabric3.spi.contribution.manifest.QNameExport;
import org.fabric3.spi.contribution.manifest.QNameImport;
import org.fabric3.spi.contribution.manifest.QNameSymbol;

/**
 *
 */
public class MetaDataStoreImplResolveResourceElementsTestCase extends TestCase {
    private static final URI EXPORTING_URI = URI.create("test-resource");
    private static final URI IMPORTING_URI = URI.create("test-resource2");
    private static final String IMPORT_EXPORT_QNAME = "test";
    private static final String NOT_VISIBLE = "notvisible";
    private MetaDataStoreImpl store;

    public void testResolve() throws Exception {
        List<ResourceElement<?, Composite>> list = store.resolve(IMPORTING_URI, Composite.class);
        assertEquals(2, list.size());
        for (ResourceElement<?, Composite> element : list) {
            assertFalse(NOT_VISIBLE.equals(element.getValue().getName().getLocalPart()));
        }
    }

    protected void setUp() throws Exception {
        super.setUp();
        store = new MetaDataStoreImpl(null);
        Map<Class<? extends Import>, ContributionWireInstantiator<?, ?, ?>> instantiators =
                new HashMap<Class<? extends Import>, ContributionWireInstantiator<?, ?, ?>>();
        instantiators.put(QNameImport.class, new QNameWireInstantiator());
        ContributionWireInstantiatorRegistryImpl instantiatorRegistry = new ContributionWireInstantiatorRegistryImpl();
        instantiatorRegistry.setInstantiators(instantiators);
        store.setInstantiatorRegistry(instantiatorRegistry);

        Contribution contribution = new Contribution(EXPORTING_URI);
        ContributionManifest manifest = contribution.getManifest();
        QNameExport export = new QNameExport(IMPORT_EXPORT_QNAME);
        manifest.addExport(export);

        createComposite(contribution, IMPORT_EXPORT_QNAME, "first");
        // create non-visible composite
        createComposite(contribution, NOT_VISIBLE, NOT_VISIBLE);
        store.store(contribution);

        Contribution contribution2 = new Contribution(IMPORTING_URI);
        ContributionManifest manifest2 = contribution2.getManifest();
        QNameImport imprt = new QNameImport(IMPORT_EXPORT_QNAME, null);
        manifest2.addImport(imprt);
        createComposite(contribution2, IMPORT_EXPORT_QNAME, "second");

        store.store(contribution2);
        imprt.addResolved(EXPORTING_URI, export);

        List<ContributionWire<?, ?>> wires = store.resolveContributionWires(IMPORTING_URI, imprt);
        for (ContributionWire<?, ?> wire : wires) {
            contribution2.addWire(wire);
        }

    }

    private void createComposite(Contribution contribution, String namespace, String name) {
        QName qName = new QName(namespace, name);
        Composite composite = new Composite(qName);
        Resource resource = new Resource(contribution, null, null);
        QNameSymbol symbol = new QNameSymbol(qName);
        ResourceElement<QNameSymbol, Composite> element = new ResourceElement<QNameSymbol, Composite>(symbol, composite);
        element.setResource(resource);
        resource.addResourceElement(element);
        contribution.addResource(resource);
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        FileHelper.deleteDirectory(new File("target/repository"));
    }

}
