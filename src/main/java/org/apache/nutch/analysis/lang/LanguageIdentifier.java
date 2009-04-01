/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nutch.analysis.lang;

// JDK imports
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.nutch.analysis.lang.NGramProfile.NGramEntry;

/**
 * Identify the language of a content, based on statistical analysis.
 * 
 * @see <a href="http://www.w3.org/WAI/ER/IG/ert/iso639.htm">ISO 639 Language
 *      Codes</a>
 * 
 * @author Sami Siren
 * @author J&eacute;r&ocirc;me Charron
 */
public class LanguageIdentifier {

	private final static int DEFAULT_ANALYSIS_LENGTH = 0; // 0 means full
	// content

	private final static float SCORE_THRESOLD = 0.00F;

	private final static Log LOG = LogFactory.getLog(LanguageIdentifier.class);

	private ArrayList languages = new ArrayList();

	private ArrayList supportedLanguages = new ArrayList();

	/** Minimum size of NGrams */
	private int minLength = NGramProfile.DEFAULT_MIN_NGRAM_LENGTH;

	/** Maximum size of NGrams */
	private int maxLength = NGramProfile.DEFAULT_MAX_NGRAM_LENGTH;

	/** The maximum amount of data to analyze */
	private int analyzeLength = DEFAULT_ANALYSIS_LENGTH;

	/** A global index of ngrams of all supported languages */
	private HashMap ngramsIdx = new HashMap();

	/** The NGramProfile used for identification */
	private NGramProfile suspect = null;

	/** My singleton instance */
	private static LanguageIdentifier identifier = null;

	private static final LanguageIdentifier INSTANCE = new LanguageIdentifier();

	public static String identifyLanguage(final String txt) {
		return INSTANCE.identify(txt);
	}

	/**
	 * Constructs a new Language Identifier.
	 */
	public LanguageIdentifier() {

		// Gets ngram sizes to take into account from the Nutch Config
		minLength = NGramProfile.DEFAULT_MIN_NGRAM_LENGTH;
		maxLength = NGramProfile.DEFAULT_MAX_NGRAM_LENGTH;
		// Ensure the min and max values are in an acceptale range
		// (ie min >= DEFAULT_MIN_NGRAM_LENGTH and max <=
		// DEFAULT_MAX_NGRAM_LENGTH)
		maxLength = Math.min(maxLength, NGramProfile.ABSOLUTE_MAX_NGRAM_LENGTH);
		maxLength = Math.max(maxLength, NGramProfile.ABSOLUTE_MIN_NGRAM_LENGTH);
		minLength = Math.max(minLength, NGramProfile.ABSOLUTE_MIN_NGRAM_LENGTH);
		minLength = Math.min(minLength, maxLength);

		// Gets the value of the maximum size of data to analyze
		analyzeLength = DEFAULT_ANALYSIS_LENGTH;

		final ClassLoader classLoader = LanguageIdentifier.class.getClassLoader();

		Properties p = new Properties();
		try {
			p.load(classLoader.getResourceAsStream("nutch/langmappings.properties"));

			Enumeration alllanguages = p.keys();

			if (LOG.isInfoEnabled()) {
				LOG.info(new StringBuffer().append("Language identifier configuration [").append(minLength).append("-")
						.append(maxLength).append("/").append(analyzeLength).append("]").toString());
			}

			StringBuffer list = new StringBuffer("Language identifier plugin supports:");
			HashMap tmpIdx = new HashMap();
			while (alllanguages.hasMoreElements()) {
				String lang = (String) (alllanguages.nextElement());

				InputStream is = classLoader.getResourceAsStream("nutch/" + lang + "." + NGramProfile.FILE_EXTENSION);

				if (is != null) {
					NGramProfile profile = new NGramProfile(lang, minLength, maxLength);
					try {
						profile.load(is);
						languages.add(profile);
						supportedLanguages.add(lang);
						List ngrams = profile.getSorted();
						for (int i = 0; i < ngrams.size(); i++) {
							NGramEntry entry = (NGramEntry) ngrams.get(i);
							List registered = (List) tmpIdx.get(entry);
							if (registered == null) {
								registered = new ArrayList();
								tmpIdx.put(entry, registered);
							}
							registered.add(entry);
							entry.setProfile(profile);
						}
						list.append(" " + lang + "(" + ngrams.size() + ")");
						is.close();
					} catch (IOException e1) {
						if (LOG.isFatalEnabled()) {
							LOG.fatal(e1.toString());
						}
					}
				}
			}
			// transform all ngrams lists to arrays for performances
			Iterator keys = tmpIdx.keySet().iterator();
			while (keys.hasNext()) {
				NGramEntry entry = (NGramEntry) keys.next();
				List l = (List) tmpIdx.get(entry);
				if (l != null) {
					NGramEntry[] array = (NGramEntry[]) l.toArray(new NGramEntry[l.size()]);
					ngramsIdx.put(entry.getSeq(), array);
				}
			}
			if (LOG.isInfoEnabled()) {
				LOG.info(list.toString());
			}
		} catch (Exception e) {
			LOG.fatal(e.toString(), e);
		}

		// Create the suspect profile
		suspect = new NGramProfile("suspect", minLength, maxLength);
	}

	/**
	 * Identify language of a content.
	 * 
	 * @param content
	 *            is the content to analyze.
	 * @return The 2 letter <a
	 *         href="http://www.w3.org/WAI/ER/IG/ert/iso639.htm">ISO 639
	 *         language code</a> (en, fi, sv, ...) of the language that best
	 *         matches the specified content.
	 */
	public String identify(String content) {
		return identify(new StringBuffer(content));
	}

	/**
	 * Identify language of a content.
	 * 
	 * @param content
	 *            is the content to analyze.
	 * @return The 2 letter <a
	 *         href="http://www.w3.org/WAI/ER/IG/ert/iso639.htm">ISO 639
	 *         language code</a> (en, fi, sv, ...) of the language that best
	 *         matches the specified content.
	 */
	public String identify(StringBuffer content) {

		StringBuffer text = content;
		if ((analyzeLength > 0) && (content.length() > analyzeLength)) {
			text = new StringBuffer().append(content);
			text.setLength(analyzeLength);
		}

		suspect.analyze(text);
		Iterator iter = suspect.getSorted().iterator();
		float topscore = Float.MIN_VALUE;
		String lang = "";
		HashMap scores = new HashMap();
		NGramEntry searched = null;

		while (iter.hasNext()) {
			searched = (NGramEntry) iter.next();
			NGramEntry[] ngrams = (NGramEntry[]) ngramsIdx.get(searched.getSeq());
			if (ngrams != null) {
				for (int j = 0; j < ngrams.length; j++) {
					NGramProfile profile = ngrams[j].getProfile();
					Float pScore = (Float) scores.get(profile);
					if (pScore == null) {
						pScore = new Float(0);
					}
					float plScore = pScore.floatValue();
					plScore += ngrams[j].getFrequency() + searched.getFrequency();
					scores.put(profile, new Float(plScore));
					if (plScore > topscore) {
						topscore = plScore;
						lang = profile.getName();
					}
				}
			}
		}
		return lang;
	}

	/**
	 * Identify language from input stream. This method uses the platform
	 * default encoding to read the input stream. For using a specific encoding,
	 * use the {@link #identify(InputStream, String)} method.
	 * 
	 * @param is
	 *            is the input stream to analyze.
	 * @return The 2 letter <a
	 *         href="http://www.w3.org/WAI/ER/IG/ert/iso639.htm">ISO 639
	 *         language code</a> (en, fi, sv, ...) of the language that best
	 *         matches the content of the specified input stream.
	 * @throws IOException
	 *             if something wrong occurs on the input stream.
	 */
	public String identify(InputStream is) throws IOException {
		return identify(is, null);
	}

	/**
	 * Identify language from input stream.
	 * 
	 * @param is
	 *            is the input stream to analyze.
	 * @param charset
	 *            is the charset to use to read the input stream.
	 * @return The 2 letter <a
	 *         href="http://www.w3.org/WAI/ER/IG/ert/iso639.htm">ISO 639
	 *         language code</a> (en, fi, sv, ...) of the language that best
	 *         matches the content of the specified input stream.
	 * @throws IOException
	 *             if something wrong occurs on the input stream.
	 */
	public String identify(InputStream is, String charset) throws IOException {

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[2048];
		int len = 0;

		while (((len = is.read(buffer)) != -1) && ((analyzeLength == 0) || (out.size() < analyzeLength))) {
			if (analyzeLength != 0) {
				len = Math.min(len, analyzeLength - out.size());
			}
			out.write(buffer, 0, len);
		}
		return identify((charset == null) ? out.toString() : out.toString(charset));
	}

}
