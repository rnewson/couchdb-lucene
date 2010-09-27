package com.github.rnewson.couchdb.lucene.util;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;



public class UtilsTest {

    @Test
    public void testSplitOnCommas() {
        assertArrayEquals(new String[]{"foo", "bar"}, Utils.splitOnCommas("foo,bar"));
    }

    @Test
    public void testSplitOnCommasWithEmbeddedCommas() {
        assertArrayEquals(new String[]{"\"fo,o\"", "bar"}, Utils.splitOnCommas("\"fo,o\",bar"));
    }

}
