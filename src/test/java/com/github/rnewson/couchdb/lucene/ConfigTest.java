/*
 * Copyright Robert Newson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.rnewson.couchdb.lucene;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.http.client.HttpClient;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

import static org.junit.Assert.*;

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
