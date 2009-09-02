package com.github.rnewson.couchdb.lucene.spring;

import net.sf.json.JSONObject;

import org.springframework.beans.factory.FactoryBean;

import com.github.rnewson.couchdb.lucene.Database;

/**
 * This class fetches configuration properties from couchdb.
 * 
 * @author rnewson
 * 
 */
public final class CouchDBConfigBean implements FactoryBean {

    private Database database;

    public void setDatabase(final Database database) {
        this.database = database;
    }

    @Override
    public Object getObject() throws Exception {
        return database.getInfo("_config");
    }

    @Override
    public Class getObjectType() {
        return JSONObject.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

}
