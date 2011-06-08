package com.github.rnewson.couchdb.lucene;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.http.client.HttpClient;
import org.apache.log4j.Logger;

public final class Config {

    private static final Logger LOG = Logger.getLogger(Config.class);

    private static final String CONFIG_FILE = "couchdb-lucene.ini";
    private static final String LUCENE_DIR = "lucene.dir";
    private static final String DEFAULT_DIR = "indexes";

    private final HierarchicalINIConfiguration configuration;

    public Config() throws ConfigurationException {
        this.configuration = new HierarchicalINIConfiguration(Config.class
                .getClassLoader().getResource(CONFIG_FILE));
        this.configuration
                .setReloadingStrategy(new FileChangedReloadingStrategy());
    }

    public final HierarchicalINIConfiguration getConfiguration() {
        return this.configuration;
    }

    public final File getDir() throws IOException {
        final File dir = new File(this.configuration.getString(LUCENE_DIR,
                DEFAULT_DIR));
        if (!dir.exists() && !dir.mkdir()) {
            throw new IOException("Could not create " + dir.getCanonicalPath());
        }
        if (!dir.canRead()) {
            throw new IOException(dir + " is not readable.");
        }
        if (!dir.canWrite()) {
            throw new IOException(dir + " is not writable.");
        }
        LOG.info("Index output goes to: " + dir.getCanonicalPath());
        return dir;
    }

    public final HttpClient getClient() throws MalformedURLException {
        HttpClientFactory.setIni(this.configuration);
        return HttpClientFactory.getInstance();
    }
}
