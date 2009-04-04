package com.github.rnewson.couchdb.lucene;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.nutch.analysis.lang.LanguageIdentifier;
import org.junit.Ignore;
import org.junit.Test;

public class LanguageIdentifierTest {

    @Test
    public void testEnglish() throws Exception {
        assertLanguage("i18n/en/ep-97-05-15.txt", "en");
    }

    @Test
    public void testDanish() throws Exception {
        assertLanguage("i18n/da/ep-04-09-16.txt", "da");
    }

    @Test
    public void testGerman() throws Exception {
        assertLanguage("i18n/de/ep-00-02-02.txt", "de");
    }

    @Test
    public void testSpanish() throws Exception {
        assertLanguage("i18n/es/ep-03-10-08.txt", "es");
    }

    @Test
    public void testGreek() throws Exception {
        assertLanguage("i18n/el/ep-04-05-03.txt", "el");
    }

    @Test
    public void testFinnish() throws Exception {
        assertLanguage("i18n/fi/ep-99-03-09.txt", "fi");

    }

    @Test
    public void testItalian() throws Exception {
        assertLanguage("i18n/it/ep-98-09-16.txt", "it");
    }

    @Test
    public void testFrench() throws Exception {
        assertLanguage("i18n/fr/ep-96-09-18.txt", "fr");
    }

    @Test
    public void testDutch() throws Exception {
        assertLanguage("i18n/nl/ep-98-09-18.txt", "nl");
    }

    @Test
    public void testPortugese() throws Exception {
        assertLanguage("i18n/pt/ep-98-09-17.txt", "pt");
    }

    @Test
    public void testSwedish() throws Exception {
        assertLanguage("i18n/sv/ep-98-09-17.txt", "sv");
    }

    @Test
    @Ignore
    public void testPerformance() throws Exception {
        final long start = System.currentTimeMillis();
        final int max = 200;
        for (int i = 0; i < max; i++) {
            assertLanguage("i18n/sv/ep-98-09-17.txt", "sv");
            assertLanguage("i18n/nl/ep-98-09-18.txt", "nl");
        }
        System.out.println((System.currentTimeMillis() - start) / max);
    }

    private void assertLanguage(final String file, final String expectedLanguage) throws Exception {
        final InputStream in = LanguageIdentifier.class.getClassLoader().getResourceAsStream(file);
        final String txt = IOUtils.toString(in, "UTF-8");
        in.close();
        assertThat(LanguageIdentifier.identifyLanguage(txt), is(expectedLanguage));
    }

}
