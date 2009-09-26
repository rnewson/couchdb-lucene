package com.github.rnewson.couchdb.lucene.util;

/**
 * Copyright 2009 Robert Newson
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
import java.util.Map;

import org.apache.commons.collections.map.ReferenceMap;
import org.apache.lucene.search.CachingWrapperFilter;
import org.apache.lucene.search.Filter;

/**
 * Keep filters around subject to memory pressure.
 * 
 * @author rnewson
 * 
 */
public final class FilterCache {

    private static final Map<Object, Object> CACHE = new ReferenceMap(ReferenceMap.HARD, ReferenceMap.SOFT);

    public static Filter get(final Object key, final Filter value) {
        CachingWrapperFilter filter;

        // Get cached filter.
        synchronized (CACHE) {
            filter = (CachingWrapperFilter) CACHE.get(key);
        }

        // Return cached filter, if possible.
        if (filter != null) {
            return filter;
        }

        // Cache miss.
        filter = new CachingWrapperFilter(value);

        synchronized (CACHE) {
            CACHE.put(key, filter);
        }

        return filter;
    }

}
