/*
 * Fabric3
 * Copyright (c) 2009-2011 Metaform Systems
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
package org.fabric3.fabric.transport;

import java.util.Collections;
import java.util.Map;

import org.osoa.sca.annotations.EagerInit;
import org.osoa.sca.annotations.Reference;

import org.fabric3.api.annotation.management.Management;
import org.fabric3.api.annotation.management.ManagementOperation;
import org.fabric3.api.annotation.monitor.Monitor;
import org.fabric3.spi.transport.Transport;

import static org.fabric3.api.annotation.management.OperationType.POST;

/**
 * @version $Rev$ $Date$
 */
@EagerInit
@Management(path = "/runtime/TransportService", description = "Manages runtime binding transports")
public class TransportService {
    private TransportServiceMonitor monitor;
    private Map<String, Transport> transports = Collections.emptyMap();

    public TransportService(@Monitor TransportServiceMonitor monitor) {
        this.monitor = monitor;
    }

    @Reference(required = false)
    public void setTransports(Map<String, Transport> transports) {
        this.transports = transports;
    }

    @ManagementOperation(type = POST, description = "Suspend a transport from receiving requests")
    public void suspend(String name) {
        Transport transport = transports.get(name);
        if (transport == null) {
            monitor.transportNotFound(name);
            return;
        }
        transport.suspend();
    }

    @ManagementOperation(type = POST, description = "Suspend all transports from receiving requests")
    public void suspendAll() {
        for (Transport transport : transports.values()) {
            transport.suspend();
        }
    }

    @ManagementOperation(type = POST, description = "Resume receiving requests for a transport")
    public void resume(String name) {
        Transport transport = transports.get(name);
        if (transport == null) {
            monitor.transportNotFound(name);
            return;
        }
        transport.resume();
    }

    @ManagementOperation(type = POST, description = "Resume receiving requests for all transports")
    public void resumeAll() {
        for (Transport transport : transports.values()) {
            transport.resume();
        }
    }
}
