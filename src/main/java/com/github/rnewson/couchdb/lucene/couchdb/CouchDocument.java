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

public class CouchDocument {

    protected final JSONObject json;

    private static final String ID = "_id";

    private static final String DELETED = "_deleted";

    public static CouchDocument deletedDocument(final String id) throws JSONException {
        final JSONObject json = new JSONObject();
        json.put(ID, id);
        json.put(DELETED, true);
        return new CouchDocument(json);
    }

    public CouchDocument(final JSONObject json) {
        if (!json.has(ID)) {
            throw new IllegalArgumentException(json + " is not a document");
        }
        this.json = json;
    }

    public String getId() throws JSONException {
        return json.getString(ID);
    }

    public boolean isDeleted() {
        return json.optBoolean(DELETED, false);
    }

    public JSONObject asJson() {
        return json;
    }

}
