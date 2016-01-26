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

package com.github.rnewson.couchdb.lucene.util;

import java.util.HashMap;
import java.util.Map;

public final class StopWatch {

    private final Map<String, Long> elapsed = new HashMap<>();

    private long start = System.nanoTime();

    public long getElapsed(final String name) {
        return elapsed.get(name) / 1000000;
    }

    public void lap(final String name) {
        final long now = System.nanoTime();
        elapsed.put(name, now - start);
        start = now;
    }

}
