package com.github.rnewson.couchdb.lucene;

import java.io.File;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

public class JSONTest {

    @Test
    public void sameOrder() throws Exception {
        final JSONArray hits25 = JSONObject.fromObject(FileUtils.readFileToString(new File("/tmp/25hits"))).getJSONArray("rows");
        final JSONArray hits50 = JSONObject.fromObject(FileUtils.readFileToString(new File("/tmp/50hits"))).getJSONArray("rows");

        for (int i = 0; i < 25; i++) {
            final String left = hits25.getJSONObject(i).getString("id");
            final String right = hits50.getJSONObject(i).getString("id");
            System.out.printf("%b: %s\t%s\n", left.equals(right), left, right);
        }
    }

}
