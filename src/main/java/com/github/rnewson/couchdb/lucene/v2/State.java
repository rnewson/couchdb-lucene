package com.github.rnewson.couchdb.lucene.v2;

import org.apache.http.client.HttpClient;

/**
 * A compound object to pass references to important stateful objects.
 * 
 * @author robertnewson
 * 
 */
public final class State {

    public final Couch couch;
    public final LuceneGateway gateway;
    public final HttpClient httpClient;
    public final Locator locator;

    public State(final Couch couch, final LuceneGateway gateway, final Locator locator, final HttpClient httpClient) {
        this.couch = couch;
        this.gateway = gateway;
        this.locator = locator;
        this.httpClient = httpClient;
    }

}
