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
package org.fabric3.jmx.agent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;

import org.oasisopen.sca.annotation.Property;
import org.oasisopen.sca.annotation.Reference;
import org.oasisopen.sca.annotation.Service;

import org.fabric3.api.Role;
import org.fabric3.api.host.runtime.ParseException;

/**
 * Delegates to a runtime extension to perform JMX authentication.
 */
@Service(DelegatingJmxAuthenticator.class)
public class DelegatingJmxAuthenticator implements JMXAuthenticator {
    private JmxSecurity security = JmxSecurity.DISABLED;
    private Set<Role> roles = new HashSet<Role>();
    private JMXAuthenticator delegate;

    @Property(required = false)
    public void setSecurity(String level) throws ParseException {
        try {
            security = JmxSecurity.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ParseException("Invalid JMX security setting:" + level);
        }
    }

    @Property(required = false)
    public void setRoles(String rolesAttribute) {
        String[] rolesString = rolesAttribute.split(",");
        for (String s : rolesString) {
            roles.add(new Role(s.trim()));
        }
    }

    /**
     * Used to obtain the JMXAuthenticator delegate when it becomes available as an extension. A collection is required since reinjection is only
     * performed on multiplicities.
     *
     * @param authenticators the authenticator
     */
    @Reference(required = false)
    public void setAuthenticators(List<JMXAuthenticator> authenticators) {
        if (authenticators.isEmpty()) {
            return;
        }
        delegate = authenticators.get(0);
    }

    public Subject authenticate(Object credentials) {
        if (delegate == null) {
            throw new SecurityException("Delegate JMXAuthenticator not configured");
        }
        Subject subject = delegate.authenticate(credentials);
        if (JmxSecurity.AUTHENTICATION == security) {
            // only perform authentication
            return subject;
        }
        if (authorize(subject)) {
            return subject;
        } else {
            throw new SecurityException("Access denied");
        }
    }

    private boolean authorize(Subject subject) {
        boolean authenticated = false;
        for (Role role : roles) {
            if (subject.getPrincipals().contains(role)) {
                authenticated = true;
                break;
            }
        }
        return authenticated;
    }
}