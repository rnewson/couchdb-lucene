package org.apache.nutch.analysis.lang;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;

import org.junit.Test;

public class LanguageIdentifierTest {

    @Test
    public void testEnglish() throws IOException {
        assertThat(detectLanguage("my head hurts"), is("en"));
        assertThat(detectLanguage("english text here"), is("en"));
    }

    @Test
    public void testGerman() throws IOException {
        assertThat(detectLanguage("Alle Menschen sind frei und gleich"), is("de"));
    }

    @Test
    public void testFrench() throws IOException {
        assertThat(detectLanguage("Me permettez-vous, dans ma gratitude"), is("fr"));
    }

    private String detectLanguage(final String text) {
        final LanguageIdentifier identifier = new LanguageIdentifier();
        return identifier.identify(text);
    }

}
