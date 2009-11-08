package com.github.rnewson.couchdb.lucene;

import org.apache.http.client.HttpClient;

import com.github.rnewson.couchdb.lucene.couchdb.Couch;

/**
 * A compound object to pass references to important stateful objects.
 * 
 * @author robertnewson
 * 
 */
public final class State {

    public final Couch couch;
    public final HttpClient httpClient;
    public final Locator locator;
    public final LuceneGateway lucene;

    public State(final Couch couch, final LuceneGateway lucene, final Locator locator, final HttpClient httpClient) {
        this.couch = couch;
        this.lucene = lucene;
        this.locator = locator;
        this.httpClient = httpClient;
    }

}
