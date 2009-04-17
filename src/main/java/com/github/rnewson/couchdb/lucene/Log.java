package com.github.rnewson.couchdb.lucene;

import org.apache.log4j.Logger;

/**
 * Copyright 2009 Robert Newson
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

public final class Log {

    private static final Logger LOG = Logger.getLogger("couchdb-lucene");

    public static void log(final String fmt, final Object... args) {
        if (LOG.isInfoEnabled()) {
            LOG.info(String.format(fmt, args));
        }
    }

    public static void log(final Exception e) {
        LOG.warn(e.getMessage(), e);
    }

}
