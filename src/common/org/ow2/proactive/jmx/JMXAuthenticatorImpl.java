/*
 * ################################################################
 *
 * ProActive: The Java(TM) library for Parallel, Distributed,
 *            Concurrent computing with Security and Mobility
 *
 * Copyright (C) 1997-2010 INRIA/University of 
 * 				Nice-Sophia Antipolis/ActiveEon
 * Contact: proactive@ow2.org or contact@activeeon.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 3 of
 * the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * If needed, contact us to obtain a release under GPL Version 2 
 * or a different license than the GPL.
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://proactive.inria.fr/team_members.htm
 *  Contributor(s):
 *
 * ################################################################
 * $$PROACTIVE_INITIAL_DEV$$
 */
package org.ow2.proactive.jmx;

import java.util.Collections;

import javax.management.remote.JMXAuthenticator;
import javax.management.remote.JMXPrincipal;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.ow2.proactive.authentication.AuthenticationImpl;
import org.ow2.proactive.authentication.crypto.Credentials;
import org.ow2.proactive.jmx.naming.JMXProperties;


/**
 * Class to perform the authentication for the JMX MBean Server.
 *
 * @author The ProActive Team
 * @since ProActive Scheduling 1.0
 */
public final class JMXAuthenticatorImpl implements JMXAuthenticator {

    /** reference to the authentication Object */
    private final AuthenticationImpl authentication;

    /**
     * Constructor to assign the values to the global variables 
     *
     * @param authentication the authentication object that is actually used 
     */
    public JMXAuthenticatorImpl(final AuthenticationImpl authentication) {
        this.authentication = authentication;
    }

    /**
     * This method is automatically called when a JMX client tries to connect to the MBean Server referred
     * by the connector, it first tries to authenticate as {@link JMXProperties#JMX_ADMIN} role and if it fails 
     * the method tries as {@link JMXProperties#JMX_USER} role.
     * <p>
     * The only allowed credentials structure provided by the client is Object[] that contains
     * username/password (String/String) or username/{@link org.ow2.proactive.authentication.crypto.Credentials} 
     * 
     * @return a subject with the username as JMXPrincipal and the role as pubCredentials {@link javax.security.auth.Subject}
     * @param rawCredentials the credentials provided by the client
     */
    public Subject authenticate(final Object rawCredentials) {
        // If not an array of object do not give any clues just throw exception 
        if (rawCredentials == null || !(rawCredentials instanceof Object[])) {
            throw new SecurityException("Invalid credentials");
        }
        final Object[] arr = (Object[]) rawCredentials;
        if (arr[0] == null || arr[1] == null) {
            throw new SecurityException("Invalid credentials");
        }
        final String username = arr[0].toString();
        Credentials internalCredentials = null;
        // If username/Credentials
        if (arr[1] instanceof Credentials) {
            internalCredentials = (Credentials) arr[1];
            // If username/password (ex: JConsole)
        } else if (arr[1] instanceof String) {
            try {
                internalCredentials = Credentials.createCredentials(username, (String) arr[1], authentication
                        .getPublicKey());
            } catch (Exception e) {
                throw new SecurityException("Invalid credentials", e);
            }
        } else {
            throw new SecurityException("Invalid credentials");
        }
        String role = null;
        // First try to authenticate as admin
        try {
            role = JMXProperties.JMX_ADMIN;
            this.authentication.loginAs(role, new String[] { role }, internalCredentials);
        } catch (LoginException le1) {
            // If failed try to authenticate as user (an admin can authenticate as a user)
            try {
                role = JMXProperties.JMX_USER;
                this.authentication.loginAs(role, new String[] { JMXProperties.JMX_ADMIN, role },
                        internalCredentials);
            } catch (LoginException le2) {
                throw new SecurityException("Unable to log as " + role);
            }
        }
        // Return a subject that contains the username and the role 
        return new Subject(true, Collections.singleton(new JMXPrincipal(username)), Collections
                .singleton(role), Collections.EMPTY_SET);
    }
}
