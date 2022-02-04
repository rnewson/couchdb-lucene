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

package com.github.rnewson.couchdb.lucene.rhino;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.mozilla.javascript.ScriptableObject;

public class JSLog extends ScriptableObject {

    private static final long serialVersionUID = 1L;
    private final Logger logger = LoggerFactory.getLogger(JSLog.class);

    public JSLog() {
        String[] names = {"error", "warn", "info", "debug", "trace"};
        defineFunctionProperties(names, JSLog.class, ScriptableObject.DONTENUM);
    }

    @Override
    public String getClassName() {
        return "LogAdapter";
    }

    public void error(String mesg) {
        logger.error(mesg);
    }

    public void warn(String mesg) {
        logger.warn(mesg);
    }

    public void info(String mesg) {
        logger.info(mesg);
    }

    public void debug(String mesg) {
        logger.debug(mesg);
    }

    public void trace(String mesg) {
        logger.trace(mesg);
    }
}

