package com.github.rnewson.couchdb.lucene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.http.client.HttpClient;
import org.junit.Test;

public class ConfigTest {

    @Test
    public void testGetConfiguration() {
        try {
            final Config config = new Config();
            HierarchicalINIConfiguration configuration = config
                    .getConfiguration();
            assertEquals("localhost", configuration.getString("lucene.host"));
            assertEquals(5985, configuration.getInt("lucene.port"));
        } catch (ConfigurationException ce) {
            fail("ConfigurationException shouldn't have been thrown."
                    + ce.getMessage());
        }
    }

    @Test
    public void testGetDir() {
        try {
            final Config config = new Config();
            File dir = config.getDir();
            assertTrue(dir.exists());
            assertTrue(dir.canRead());
            assertTrue(dir.canWrite());
            assertEquals(new File("target", "indexes"), dir);
        } catch (ConfigurationException ce) {
            fail("ConfigurationException shouldn't have been thrown."
                    + ce.getMessage());
        } catch (IOException ioe) {
            fail("IOException shouldn't have been thrown." + ioe.getMessage());
        }
    }

    @Test
    public void testGetClient() {
        try {
            final Config config = new Config();
            HttpClient client = config.getClient();
            assertNotNull(client);
        } catch (ConfigurationException ce) {
            fail("ConfigurationException shouldn't have been thrown."
                    + ce.getMessage());
        } catch (MalformedURLException mue) {
            fail("MalformedURLException shouldn't have been thrown."
                    + mue.getMessage());
        }
    }
}
