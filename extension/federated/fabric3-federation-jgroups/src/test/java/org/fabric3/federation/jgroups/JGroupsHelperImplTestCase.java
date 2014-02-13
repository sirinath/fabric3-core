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
package org.fabric3.federation.jgroups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;
import org.easymock.EasyMock;
import org.fabric3.spi.classloader.ClassLoaderRegistry;
import org.fabric3.spi.federation.topology.RuntimeInstance;
import org.fabric3.spi.federation.topology.Zone;
import org.jgroups.Address;
import org.jgroups.View;
import org.jgroups.ViewId;
import org.jgroups.util.UUID;

/**
 *
 */
public class JGroupsHelperImplTestCase extends TestCase {
    JGroupsHelperImpl helper;

    org.jgroups.stack.IpAddress coord_addr = new org.jgroups.stack.IpAddress(9999);

    public void testZoneName() throws Exception {
        helper.setRuntimeType("node");
        UUID address = UUID.randomUUID();
        UUID.add(address, "domain:node:zone1:1");

        assertEquals("zone1", helper.getZoneName(address));
    }

    public void testZoneLeaderNodeNaming() throws Exception {
        helper.setRuntimeType("node");
        ViewId id = new ViewId(coord_addr, 123);
        List<Address> members = new ArrayList<>();
        UUID address1 = UUID.randomUUID();
        UUID.add(address1, "domain:node:zone1:1");
        UUID address2 = UUID.randomUUID();
        UUID.add(address2, "domain:node:zone1:2");
        UUID address3 = UUID.randomUUID();
        UUID.add(address3, "domain:node:zone2:3");
        members.add(address1);
        members.add(address2);
        members.add(address3);
        View view = new View(id, members);

        Address zone1Leader = helper.getZoneLeader("zone1", view);
        Address zone2Leader = helper.getZoneLeader("zone2", view);
        assertEquals(address1, zone1Leader);
        assertEquals(address3, zone2Leader);
    }

    public void testZoneLeader() throws Exception {
        ViewId id = new ViewId(coord_addr, 123);
        List<Address> members = new ArrayList<>();
        UUID address1 = UUID.randomUUID();
        UUID.add(address1, "domain:participant:zone1:1");
        UUID address2 = UUID.randomUUID();
        UUID.add(address2, "domain:participant:zone1:2");
        UUID address3 = UUID.randomUUID();
        UUID.add(address3, "domain:participant:zone2:3");
        members.add(address1);
        members.add(address2);
        members.add(address3);
        View view = new View(id, members);

        Address zone1Leader = helper.getZoneLeader("zone1", view);
        Address zone2Leader = helper.getZoneLeader("zone2", view);
        assertEquals(address1, zone1Leader);
        assertEquals(address3, zone2Leader);
    }

    public void testRuntimeAddressesInZoneNodeNaming() throws Exception {
        helper.setRuntimeType("node");
        ViewId id = new ViewId(coord_addr, 123);
        List<Address> members = new ArrayList<>();
        UUID address1 = UUID.randomUUID();
        UUID.add(address1, "domain:node:zone1:1");
        UUID address2 = UUID.randomUUID();
        UUID.add(address2, "domain:node:zone1:2");
        UUID address3 = UUID.randomUUID();
        UUID.add(address3, "domain:node:zone2:3");
        members.add(address1);
        members.add(address2);
        members.add(address3);
        View view = new View(id, members);

        List<Address> addresses = helper.getRuntimeAddressesInZone("zone1", view);
        assertEquals(2, addresses.size());
        for (Address leader : addresses) {
            assertTrue(leader.equals(address1) || leader.equals(address2));
        }
    }

    public void testRuntimeAddressesInZone() throws Exception {
        ViewId id = new ViewId(coord_addr, 123);
        List<Address> members = new ArrayList<>();
        UUID address1 = UUID.randomUUID();
        UUID.add(address1, "domain:participant:zone1:1");
        UUID address2 = UUID.randomUUID();
        UUID.add(address2, "domain:participant:zone1:2");
        UUID address3 = UUID.randomUUID();
        UUID.add(address3, "domain:participant:zone2:3");
        members.add(address1);
        members.add(address2);
        members.add(address3);
        View view = new View(id, members);

        List<Address> addresses = helper.getRuntimeAddressesInZone("zone1", view);
        assertEquals(2, addresses.size());
        for (Address leader : addresses) {
            assertTrue(leader.equals(address1) || leader.equals(address2));
        }
    }

    public void testNewRuntimes() throws Exception {
        ViewId oldId = new ViewId(coord_addr, 123);
        ViewId newId = new ViewId(coord_addr, 456);
        List<Address> newMembers = new ArrayList<>();
        List<Address> oldMembers = new ArrayList<>();
        UUID address1 = UUID.randomUUID();
        UUID address2 = UUID.randomUUID();
        oldMembers.add(address1);
        newMembers.add(address2);
        newMembers.add(address1);
        View oldView = new View(oldId, oldMembers);
        View newView = new View(newId, newMembers);

        Set<Address> newRuntimes = helper.getNewRuntimes(oldView, newView);
        assertEquals(1, newRuntimes.size());
        assertEquals(address2, newRuntimes.iterator().next());
    }

    public void testRemovedRuntimes() throws Exception {
        ViewId oldId = new ViewId(coord_addr, 123);
        ViewId newId = new ViewId(coord_addr, 456);
        List<Address> newMembers = new ArrayList<>();
        List<Address> oldMembers = new ArrayList<>();
        UUID address1 = UUID.randomUUID();
        UUID address2 = UUID.randomUUID();
        oldMembers.add(address1);
        oldMembers.add(address2);
        newMembers.add(address2);
        View oldView = new View(oldId, oldMembers);
        View newView = new View(newId, newMembers);

        Set<Address> removedRuntimes = helper.getRemovedRuntimes(oldView, newView);
        assertEquals(1, removedRuntimes.size());
        assertEquals(address1, removedRuntimes.iterator().next());
    }

    public void testRemovedRuntimesOnBootstrap() throws Exception {
        ViewId newId = new ViewId(coord_addr, 456);
        List<Address> newMembers = new ArrayList<>();
        UUID address1 = UUID.randomUUID();
        UUID address2 = UUID.randomUUID();
        newMembers.add(address1);
        newMembers.add(address2);
        View newView = new View(newId, newMembers);

        Set<Address> removedRuntimes = helper.getRemovedRuntimes(null, newView);
        assertTrue(removedRuntimes.isEmpty());
    }

    public void testNewRuntimesOnBootstrap() throws Exception {
        ViewId newId = new ViewId(coord_addr, 456);
        List<Address> newMembers = new ArrayList<>();
        UUID address1 = UUID.randomUUID();
        UUID address2 = UUID.randomUUID();
        newMembers.add(address1);
        newMembers.add(address2);
        View newView = new View(newId, newMembers);

        // old view is null as a previous view did not exist
        Set<Address> newRuntimes = helper.getNewRuntimes(null, newView);
        assertEquals(2, newRuntimes.size());
    }

    public void testNoNewRuntimes() throws Exception {
        ViewId oldId = new ViewId(coord_addr, 123);
        ViewId newId = new ViewId(coord_addr, 456);
        List<Address> newMembers = new ArrayList<>();
        List<Address> oldMembers = new ArrayList<>();
        UUID address1 = UUID.randomUUID();
        UUID address2 = UUID.randomUUID();
        oldMembers.add(address1);
        oldMembers.add(address2);
        newMembers.add(address2);
        newMembers.add(address1);
        View oldView = new View(oldId, oldMembers);
        View newView = new View(newId, newMembers);

        Set<Address> newRuntimes = helper.getNewRuntimes(oldView, newView);
        assertEquals(0, newRuntimes.size());
    }

    public void testNewZoneLeaders() throws Exception {
        ViewId oldId = new ViewId(coord_addr, 123);
        ViewId newId = new ViewId(coord_addr, 456);
        List<Address> newMembers = new ArrayList<>();
        List<Address> oldMembers = new ArrayList<>();
        UUID address1 = UUID.randomUUID();
        UUID.add(address1, "domain:participant:zone:1");
        UUID address2 = UUID.randomUUID();
        UUID.add(address2, "domain:participant:zone:2");
        UUID address3 = UUID.randomUUID();
        UUID.add(address3, "domain:participant:zone:3");
        UUID address4 = UUID.randomUUID();
        UUID.add(address4, "domain:participant:zone2:3");
        oldMembers.add(address1);
        newMembers.add(address2);
        newMembers.add(address3);
        newMembers.add(address4);
        View oldView = new View(oldId, oldMembers);
        View newView = new View(newId, newMembers);

        Set<Address> leaders = helper.getNewZoneLeaders(oldView, newView);
        assertEquals(2, leaders.size());
        for (Address leader : leaders) {
            assertTrue(leader.equals(address2) || leader.equals(address4));
        }
    }

    public void testNewZoneLeadersOnBootstrap() throws Exception {
        ViewId newId = new ViewId(coord_addr, 456);
        List<Address> newMembers = new ArrayList<>();
        UUID address1 = UUID.randomUUID();
        UUID.add(address1, "domain:participant:zone:1");
        UUID address2 = UUID.randomUUID();
        UUID.add(address2, "domain:participant:zone:2");
        UUID address3 = UUID.randomUUID();
        UUID.add(address3, "domain:participant:zone:3");
        UUID address4 = UUID.randomUUID();
        UUID.add(address4, "domain:participant:zone2:3");
        newMembers.add(address2);
        newMembers.add(address3);
        newMembers.add(address4);
        View newView = new View(newId, newMembers);

        // old view is null as a previous view did not exist
        Set<Address> leaders = helper.getNewZoneLeaders(null, newView);
        assertEquals(2, leaders.size());
        for (Address leader : leaders) {
            assertTrue(leader.equals(address2) || leader.equals(address4));
        }
    }

    public void testGetZones() throws Exception {
        Map<String, Map<String, RuntimeInstance>> runtimes = new HashMap<>();
        runtimes.put("zone1", Collections.singletonMap("runtime1", new RuntimeInstance("runtime1")));
        runtimes.put("zone2", Collections.singletonMap("runtime2", new RuntimeInstance("runtime2")));

        Set<Zone> zones = helper.getZones(runtimes);
        assertEquals(2, zones.size());
    }

    protected void setUp() throws Exception {
        super.setUp();
        ClassLoaderRegistry registry = EasyMock.createNiceMock(ClassLoaderRegistry.class);
        helper = new JGroupsHelperImpl(registry);
    }

}