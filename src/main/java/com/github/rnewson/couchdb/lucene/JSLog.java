package com.github.rnewson.couchdb.lucene;

import org.apache.log4j.Logger;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

class JSLog extends ScriptableObject {

    private final Logger logger = Logger.getLogger(LogAdapter.class);

    public JSLog() {
        String[] names = { "error", "warn", "info", "debug", "trace" };
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

