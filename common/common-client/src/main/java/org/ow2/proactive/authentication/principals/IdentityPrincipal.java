/*
 * ProActive Parallel Suite(TM):
 * The Open Source library for parallel and distributed
 * Workflows & Scheduling, Orchestration, Cloud Automation
 * and Big Data Analysis on Enterprise Grids & Clouds.
 *
 * Copyright (c) 2007 - 2017 ActiveEon
 * Contact: contact@activeeon.com
 *
 * This library is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation: version 3 of
 * the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 */
package org.ow2.proactive.authentication.principals;

import java.io.Serializable;
import java.security.Principal;


public class IdentityPrincipal implements Principal, Serializable {

    private String name;

    public IdentityPrincipal(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return (this.getClass().getSimpleName() + " \"" + name + "\"");
    }

    public boolean equals(Object o) {
        if (o == null)
            return false;

        if (this == o)
            return true;

        if (!o.getClass().getName().equals(this.getClass().getName()))
            return false;

        IdentityPrincipal that = (IdentityPrincipal) o;

        if (this.getName().equals(that.getName()))
            return true;

        return false;
    }

    public int hashCode() {
        return name.hashCode();
    }
}
