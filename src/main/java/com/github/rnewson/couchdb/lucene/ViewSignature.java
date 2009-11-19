package com.github.rnewson.couchdb.lucene;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A ViewSignature is an opaque object intended to be used as a key to a Lucene
 * index.
 * 
 * @author robertnewson
 * 
 */
public final class ViewSignature {

    /**
     * Increment this to invalidate all existing indexes and force a rebuild.
     * Only do this if the indexing strategy changes in an incompatible fashion!
     */
    private static final byte VERSION = 0;

    private final String view;

    /**
     * Creates a {@link ViewSignature} derived from a Javascript view function.
     * 
     * @param dbname
     * @param viewFunction
     * @return
     */
    public ViewSignature(final String viewFunction) {
        try {
            final MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(VERSION);
            md.update(viewFunction.replaceAll("\\s+", "").getBytes("UTF-8"));
            final byte[] digest = md.digest();
            view = new BigInteger(1, digest).toString(Character.MAX_RADIX);
        } catch (final NoSuchAlgorithmException e) {
            throw new Error("MD5 support missing.");
        } catch (final UnsupportedEncodingException e) {
            throw new Error("UTF-8 support missing.");
        }
    }
    
    

}
