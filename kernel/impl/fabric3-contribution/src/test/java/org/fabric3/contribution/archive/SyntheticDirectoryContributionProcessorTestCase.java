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
package org.fabric3.contribution.archive;

import java.net.URI;
import java.net.URL;

import junit.framework.TestCase;
import org.oasisopen.sca.annotation.EagerInit;

import org.fabric3.api.host.stream.Source;
import org.fabric3.api.host.stream.UrlSource;
import org.fabric3.spi.contribution.Contribution;
import org.fabric3.spi.introspection.DefaultIntrospectionContext;

/**
 *
 */
@EagerInit
public class SyntheticDirectoryContributionProcessorTestCase extends TestCase {
    private SyntheticDirectoryContributionProcessor processor;
    private Contribution contribution;
    private DefaultIntrospectionContext context;

    public void testCanProcess() throws Exception {
        assertTrue(processor.canProcess(contribution));
    }

    public void testProcessManifest() throws Exception {
        processor.processManifest(contribution, context);
        assertTrue(contribution.getManifest().getExtends().contains("1"));
    }

    public void testIndex() throws Exception {
        processor.index(contribution, context);
    }

    public void testProcess() throws Exception {
        processor.process(contribution, context);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        URI uri = URI.create("contribution");
        URL url = getClass().getResource("/repository/1");
        Source source = new UrlSource(url);
        contribution = new Contribution(uri, source, url, -1, "application/vnd.fabric3.synthetic", false);

        processor = new SyntheticDirectoryContributionProcessor();
        context = new DefaultIntrospectionContext();
    }
}