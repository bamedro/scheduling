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
package org.ow2.proactive_grid_cloud_portal.cli.cmd.sched;

import static org.ow2.proactive_grid_cloud_portal.cli.CLIException.REASON_INVALID_ARGUMENTS;

import java.util.Map;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.ow2.proactive_grid_cloud_portal.cli.CLIException;

import com.google.common.collect.Maps;


public class JobKeyValueTransformer {
    private final static String USAGE = " The correct format is [ submit('workflow.xml','{\"var1\":\"value1\",\"var2\":\"value2\"}') ]";

    public Map<String, String> transformVariablesToMap(String jsonVariables) {
        Map<String, String> jobVariables = Maps.newHashMap();
        if (jsonVariables != null) {
            try {
                jobVariables = (JSONObject) new JSONParser().parse(jsonVariables);
                validateJSONVariables(jobVariables);
            } catch (ParseException | IllegalArgumentException e) {

                throw new CLIException(REASON_INVALID_ARGUMENTS, e.getMessage() + USAGE);
            }
        }
        return jobVariables;
    }

    private void validateJSONVariables(Map<String, String> jsonVariables) {
        if (jsonVariables.size() == 0) {
            throw new IllegalArgumentException("empty json ");
        } else {
            for (String key : jsonVariables.keySet()) {
                if (key.length() == 0) {
                    throw new IllegalArgumentException("empty key ");
                }

            }
        }
    }
}
