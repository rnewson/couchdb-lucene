/**
 * 
 */
package com.github.rnewson.couchdb.lucene.util;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;

/**
 * Just return the status code (mostly used for PUT calls).
 * 
 * @author rnewson
 * 
 */
public final class StatusCodeResponseHandler implements ResponseHandler<Integer> {

    @Override
    public Integer handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
        return response.getStatusLine().getStatusCode();
    }

}