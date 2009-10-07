package com.github.rnewson.couchdb.lucene;

import org.apache.http.client.HttpClient;

/**
 * A compound object to pass references to important stateful objects.
 * 
 * @author robertnewson
 * 
 */
public final class State {

    public final Couch couch;
    public final LuceneGateway lucene;
    public final HttpClient httpClient;
    public final Locator locator;
    public final Tika tika;

    public State(final Couch couch, final LuceneGateway lucene, final Locator locator, final HttpClient httpClient, final Tika tika) {
        this.couch = couch;
        this.lucene = lucene;
        this.locator = locator;
        this.httpClient = httpClient;
        this.tika = tika;
    }

}
