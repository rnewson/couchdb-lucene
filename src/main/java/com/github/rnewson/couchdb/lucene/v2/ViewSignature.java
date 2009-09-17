package com.github.rnewson.couchdb.lucene.v2;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * A ViewSignature is an opaque object intended to be used as a key to a Lucene index.
 * 
 * @author robertnewson
 *
 */
public final class ViewSignature {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final String dbname;
    
    private final byte[] bytes;

    public ViewSignature(final String dbname, final String viewFunction) {
        try {
            final MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(dbname.getBytes(UTF8));
            md.update((byte) 0);
            md.update(viewFunction.replaceAll("\\s+", "").getBytes("UTF-8"));
            this.dbname = dbname;
            bytes = md.digest();
        } catch (final NoSuchAlgorithmException e) {
            throw new Error("MD5 support missing.");
        } catch (final UnsupportedEncodingException e) {
            throw new Error("UTF-8 support missing.");
        }
    }
    
    public File toFile(final File base) {
        return new File(new File(base, dbname), this + ".index");
    }

    @Override
    public String toString() {
        return new BigInteger(1, bytes).toString(16);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(bytes);
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
        if (!Arrays.equals(bytes, other.bytes))
            return false;
        return true;
    }

}
