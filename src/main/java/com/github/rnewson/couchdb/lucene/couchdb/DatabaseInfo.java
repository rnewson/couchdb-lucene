/*
 * Copyright Robert Newson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.rnewson.couchdb.lucene.couchdb;

import org.json.JSONException;
import org.json.JSONObject;

public class DatabaseInfo {

    private final JSONObject json;

    public DatabaseInfo(final JSONObject json) {
        this.json = json;
    }

    public UpdateSequence getUpdateSequence() throws JSONException {
        return UpdateSequence.parseUpdateSequence(json.getString("update_seq"));
    }

    public String getName() throws JSONException {
        return json.getString("db_name");
    }

}
