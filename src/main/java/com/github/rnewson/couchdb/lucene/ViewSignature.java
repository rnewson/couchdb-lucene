package com.github.rnewson.couchdb.lucene;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.charset.Charset;
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

    private final String dbname;

    private final String view;

    /**
     * Creates a {@link ViewSignature} derived from a Javascript view function.
     * 
     * @param dbname
     * @param viewFunction
     * @return
     */
    public ViewSignature(final String dbname, final String viewFunction) {
        try {
            final MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(dbname.getBytes("UTF-8"));
            md.update(VERSION);
            md.update(viewFunction.replaceAll("\\s+", "").getBytes("UTF-8"));
            final byte[] digest = md.digest();
            this.dbname = dbname;
            view = new BigInteger(1, digest).toString(16);
        } catch (final NoSuchAlgorithmException e) {
            throw new Error("MD5 support missing.");
        } catch (final UnsupportedEncodingException e) {
            throw new Error("UTF-8 support missing.");
        }
    }

    public String getDatabaseName() {
        return dbname;
    }

    public File toFile(final File base) {
        return new File(new File(base, dbname), this + ".index");
    }

    @Override
    public String toString() {
        return view;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((dbname == null) ? 0 : dbname.hashCode());
        result = prime * result + ((view == null) ? 0 : view.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ViewSignature other = (ViewSignature) obj;
        if (dbname == null) {
            if (other.dbname != null)
                return false;
        } else if (!dbname.equals(other.dbname))
            return false;
        if (view == null) {
            if (other.view != null)
                return false;
        } else if (!view.equals(other.view))
            return false;
        return true;
    }

}
