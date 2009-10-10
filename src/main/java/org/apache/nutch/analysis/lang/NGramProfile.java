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
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Token;

/**
 * This class runs a ngram analysis over submitted text, results might be used
 * for automatic language identifiaction.
 * 
 * The similarity calculation is at experimental level. You have been warned.
 * 
 * Methods are provided to build new NGramProfiles profiles.
 * 
 * @author Sami Siren
 * @author Jerome Charron - http://frutch.free.fr/
 */
public class NGramProfile {

    private class QuickStringBuffer implements CharSequence {

        private int count;

        private char value[];

        QuickStringBuffer() {
            this(16);
        }

        QuickStringBuffer(final char[] value) {
            this.value = value;
            count = value.length;
        }

        QuickStringBuffer(final int length) {
            value = new char[length];
        }

        QuickStringBuffer(final String str) {
            this(str.length() + 16);
            append(str);
        }

        public char charAt(final int index) {
            return value[index];
        }

        public int length() {
            return count;
        }

        public CharSequence subSequence(final int start, final int end) {
            return new String(value, start, end - start);
        }

        @Override
        public String toString() {
            return new String(this.value);
        }

        private void expandCapacity(final int minimumCapacity) {
            int newCapacity = (value.length + 1) * 2;
            if (newCapacity < 0) {
                newCapacity = Integer.MAX_VALUE;
            } else if (minimumCapacity > newCapacity) {
                newCapacity = minimumCapacity;
            }

            final char newValue[] = new char[newCapacity];
            System.arraycopy(value, 0, newValue, 0, count);
            value = newValue;
        }

        QuickStringBuffer append(final char c) {
            final int newcount = count + 1;
            if (newcount > value.length) {
                expandCapacity(newcount);
            }
            value[count++] = c;
            return this;
        }

        QuickStringBuffer append(String str) {
            if (str == null) {
                str = String.valueOf(str);
            }

            final int len = str.length();
            final int newcount = count + len;
            if (newcount > value.length) {
                expandCapacity(newcount);
            }
            str.getChars(0, len, value, count);
            count = newcount;
            return this;
        }

        QuickStringBuffer clear() {
            count = 0;
            return this;
        }
    }

    /**
     * Inner class that describes a NGram
     */
    class NGramEntry implements Comparable {

        /** The number of occurences of this ngram in its profile */
        private int count = 0;

        /** The frequency of this ngram in its profile */
        private float frequency = 0.0F;

        /** The NGRamProfile this NGram is related to */
        private NGramProfile profile = null;

        /** The sequence of characters of the ngram */
        CharSequence seq = null;

        /**
         * Constructs a new NGramEntry
         * 
         * @param seq
         *            is the sequence of characters of the ngram
         */
        public NGramEntry(final CharSequence seq) {
            this.seq = seq;
        }

        /**
         * Constructs a new NGramEntry
         * 
         * @param seq
         *            is the sequence of characters of the ngram
         * @param count
         *            is the number of occurences of this ngram
         */
        public NGramEntry(final String seq, final int count) {
            this.seq = new StringBuffer(seq).subSequence(0, seq.length());
            this.count = count;
        }

        // Inherited JavaDoc
        public int compareTo(final Object o) {
            final NGramEntry ngram = (NGramEntry) o;
            final int diff = Float.compare(ngram.getFrequency(), frequency);
            if (diff != 0) {
                return diff;
            } else {
                return (toString().compareTo(ngram.toString()));
            }
        }

        // Inherited JavaDoc
        @Override
        public boolean equals(final Object obj) {

            NGramEntry ngram = null;
            try {
                ngram = (NGramEntry) obj;
                return ngram.seq.equals(seq);
            } catch (final Exception e) {
                return false;
            }
        }

        /**
         * Returns the number of occurences of this ngram in its profile
         * 
         * @return the number of occurences of this ngram in its profile
         */
        public int getCount() {
            return count;
        }

        /**
         * Returns the frequency of this ngram in its profile
         * 
         * @return the frequency of this ngram in its profile
         */
        public float getFrequency() {
            return frequency;
        }

        /**
         * Returns the profile associated to this ngram
         * 
         * @return the profile associated to this ngram
         */
        public NGramProfile getProfile() {
            return profile;
        }

        /**
         * Returns the sequence of characters of this ngram
         * 
         * @return the sequence of characters of this ngram
         */
        public CharSequence getSeq() {
            return seq;
        }

        // Inherited JavaDoc
        @Override
        public int hashCode() {
            return seq.hashCode();
        }

        /**
         * Increments the number of occurences of this ngram.
         */
        public void inc() {
            count++;
        }

        /**
         * Associated a profile to this ngram
         * 
         * @param profile
         *            is the profile associated to this ngram
         */
        public void setProfile(final NGramProfile profile) {
            this.profile = profile;
        }

        /**
         * Returns the size of this ngram
         * 
         * @return the size of this ngram
         */
        public int size() {
            return seq.length();
        }

        // Inherited JavaDoc
        @Override
        public String toString() {
            return seq.toString();
        }

    }

    private static final Logger LOGGER = Logger.getLogger(NGramProfile.class);

    /** separator char */
    static final char SEPARATOR = '_';

    /** The String form of the separator char */
    private final static String SEP_CHARSEQ = new String(new char[] { SEPARATOR });

    /** The maximum length allowed for a ngram. */
    final static int ABSOLUTE_MAX_NGRAM_LENGTH = 4;

    /** The minimum length allowed for a ngram. */
    final static int ABSOLUTE_MIN_NGRAM_LENGTH = 1;

    /** The default max length of ngram */
    final static int DEFAULT_MAX_NGRAM_LENGTH = 3;

    /** The default min length of ngram */
    final static int DEFAULT_MIN_NGRAM_LENGTH = 3;
    /** The ngram profile file extension */
    static final String FILE_EXTENSION = "ngp";

    /** The profile max size (number of ngrams of the same size) */
    static final int MAX_SIZE = 1000;

    /**
     * Create a new Language profile from (preferably quite large) text file
     * 
     * @param name
     *            is thename of profile
     * @param is
     *            is the stream to read
     * @param encoding
     *            is the encoding of stream
     */
    public static NGramProfile create(final String name, final InputStream is, final String encoding) {

        final NGramProfile newProfile = new NGramProfile(name, ABSOLUTE_MIN_NGRAM_LENGTH, ABSOLUTE_MAX_NGRAM_LENGTH);
        final BufferedInputStream bis = new BufferedInputStream(is);

        final byte buffer[] = new byte[4096];
        final StringBuffer text = new StringBuffer();
        int len;

        try {
            while ((len = bis.read(buffer)) != -1) {
                text.append(new String(buffer, 0, len, encoding));
            }
        } catch (final IOException e) {
            LOGGER.warn("Exception raised while creating profile.", e);
        }

        newProfile.analyze(text);
        return newProfile;
    }

    /**
     * main method used for testing only
     * 
     * @param args
     */
    public static void main(final String args[]) throws Exception {

        final String usage = "Usage: NGramProfile " + "[-create profilename filename encoding] " + "[-similarity file1 file2] "
                + "[-score profile-name filename encoding]";
        int command = 0;

        final int CREATE = 1;
        final int SIMILARITY = 2;
        final int SCORE = 3;

        String profilename = "";
        String filename = "";
        String filename2 = "";
        String encoding = "";

        if (args.length == 0) {
            System.err.println(usage);
            System.exit(-1);
        }

        for (int i = 0; i < args.length; i++) { // parse command line
            if (args[i].equals("-create")) { // found -create option
                command = CREATE;
                profilename = args[++i];
                filename = args[++i];
                encoding = args[++i];
            }

            if (args[i].equals("-similarity")) { // found -similarity option
                command = SIMILARITY;
                filename = args[++i];
                filename2 = args[++i];
                encoding = args[++i];
            }

            if (args[i].equals("-score")) { // found -Score option
                command = SCORE;
                profilename = args[++i];
                filename = args[++i];
                encoding = args[++i];
            }
        }

        switch (command) {

        case CREATE:

            File f = new File(filename);
            FileInputStream fis = new FileInputStream(f);
            NGramProfile newProfile = NGramProfile.create(profilename, fis, encoding);
            fis.close();
            f = new File(profilename + "." + FILE_EXTENSION);
            final FileOutputStream fos = new FileOutputStream(f);
            newProfile.save(fos);
            System.out.println("new profile " + profilename + "." + FILE_EXTENSION + " was created.");
            break;

        case SIMILARITY:

            f = new File(filename);
            fis = new FileInputStream(f);
            newProfile = NGramProfile.create(filename, fis, encoding);
            newProfile.normalize();

            f = new File(filename2);
            fis = new FileInputStream(f);
            final NGramProfile newProfile2 = NGramProfile.create(filename2, fis, encoding);
            newProfile2.normalize();
            System.out.println("Similarity is " + newProfile.getSimilarity(newProfile2));
            break;

        case SCORE:
            f = new File(filename);
            fis = new FileInputStream(f);
            newProfile = NGramProfile.create(filename, fis, encoding);

            f = new File(profilename + "." + FILE_EXTENSION);
            fis = new FileInputStream(f);
            final NGramProfile compare = new NGramProfile(profilename, DEFAULT_MIN_NGRAM_LENGTH, DEFAULT_MAX_NGRAM_LENGTH);
            compare.load(fis);
            System.out.println("Score is " + compare.getSimilarity(newProfile));
            break;

        }
    }

    /** The max length of ngram */
    private int maxLength = DEFAULT_MAX_NGRAM_LENGTH;

    /** The min length of ngram */
    private int minLength = DEFAULT_MIN_NGRAM_LENGTH;

    /** The profile's name */
    private String name = null;

    /** The total number of ngrams occurences */
    private int[] ngramcounts = null;

    /** An index of the ngrams of the profile */
    private Map ngrams = null;

    /** The NGrams of this profile sorted on the number of occurences */
    private List sorted = null;

    /** A StringBuffer used during analysis */
    private final QuickStringBuffer word = new QuickStringBuffer();

    /**
     * Construct a new ngram profile
     * 
     * @param name
     *            is the name of the profile
     * @param minlen
     *            is the min length of ngram sequences
     * @param maxlen
     *            is the max length of ngram sequences
     */
    public NGramProfile(final String name, final int minlen, final int maxlen) {
        // TODO: Compute the initial capacity using minlen and maxlen.
        this.ngrams = new HashMap(4000);
        this.minLength = minlen;
        this.maxLength = maxlen;
        this.name = name;
    }

    /**
     * Add ngrams from a single word to this profile
     * 
     * @param word
     *            is the word to add
     */
    public void add(final StringBuffer word) {
        for (int i = minLength; (i <= maxLength) && (i < word.length()); i++) {
            add(word, i);
        }
    }

    /**
     * Add ngrams from a token to this profile
     * 
     * @param t
     *            is the Token to be added
     */
    public void add(final Token t) {
        add(new StringBuffer().append(SEPARATOR).append(t.termText()).append(SEPARATOR));
    }

    /**
     * Analyze a piece of text
     * 
     * @param text
     *            the text to be analyzed
     */
    public void analyze(final StringBuffer text) {

        if (ngrams != null) {
            ngrams.clear();
            sorted = null;
            ngramcounts = null;
        }

        word.clear().append(SEPARATOR);
        for (int i = 0; i < text.length(); i++) {
            final char c = Character.toLowerCase(text.charAt(i));

            if (Character.isLetter(c)) {
                add(word.append(c));
            } else {
                // found word boundary
                if (word.length() > 1) {
                    // we have a word!
                    add(word.append(SEPARATOR));
                    word.clear().append(SEPARATOR);
                }
            }
        }

        if (word.length() > 1) {
            // we have a word!
            add(word.append(SEPARATOR));
        }
        normalize();
    }

    /**
     * @return Returns the name.
     */
    public String getName() {
        return name;
    }

    /**
     * Calculate a score how well NGramProfiles match each other
     * 
     * @param another
     *            ngram profile to compare against
     * @return similarity 0=exact match
     */
    public float getSimilarity(final NGramProfile another) {

        float sum = 0;

        try {
            Iterator i = another.getSorted().iterator();
            while (i.hasNext()) {
                final NGramEntry other = (NGramEntry) i.next();
                if (ngrams.containsKey(other.seq)) {
                    sum += Math.abs((other.frequency - ((NGramEntry) ngrams.get(other.seq)).frequency)) / 2;
                } else {
                    sum += other.frequency;
                }
            }
            i = getSorted().iterator();
            while (i.hasNext()) {
                final NGramEntry other = (NGramEntry) i.next();
                if (another.ngrams.containsKey(other.seq)) {
                    sum += Math.abs((other.frequency - ((NGramEntry) another.ngrams.get(other.seq)).frequency)) / 2;
                } else {
                    sum += other.frequency;
                }
            }
        } catch (final Exception e) {
            LOGGER.warn(e);
        }
        return sum;
    }

    /**
     * Return a sorted list of ngrams (sort done by 1. frequency 2. sequence)
     * 
     * @return sorted vector of ngrams
     */
    public List getSorted() {
        // make sure sorting is done only once
        if (sorted == null) {
            sorted = new ArrayList(ngrams.values());
            Collections.sort(sorted);

            // trim at NGRAM_LENGTH entries
            if (sorted.size() > MAX_SIZE) {
                sorted = sorted.subList(0, MAX_SIZE);
            }
        }
        return sorted;
    }

    /**
     * Loads a ngram profile from an InputStream (assumes UTF-8 encoded content)
     * 
     * @param is
     *            the InputStream to read
     */
    public void load(final InputStream is) throws IOException {

        ngrams.clear();
        ngramcounts = new int[maxLength + 1];
        final BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        String line = null;

        while ((line = reader.readLine()) != null) {

            // # starts a comment line
            if (line.charAt(0) != '#') {
                final int spacepos = line.indexOf(' ');
                final String ngramsequence = line.substring(0, spacepos).trim();
                final int len = ngramsequence.length();
                if ((len >= minLength) && (len <= maxLength)) {
                    final int ngramcount = Integer.parseInt(line.substring(spacepos + 1));
                    final NGramEntry en = new NGramEntry(ngramsequence, ngramcount);
                    ngrams.put(en.getSeq(), en);
                    ngramcounts[len] += ngramcount;
                }
            }
        }
        normalize();
    }

    /**
     * Writes NGramProfile content into OutputStream, content is outputted with
     * UTF-8 encoding
     * 
     * @param os
     *            the Stream to output to
     * @throws IOException
     */
    public void save(final OutputStream os) throws IOException {

        // Write header
        os.write(("# NgramProfile generated at " + new Date() + " for Nutch Language Identification\n").getBytes());

        // And then each ngram

        // First dispatch ngrams in many lists depending on their size
        // (one list for each size, in order to store MAX_SIZE ngrams for each
        // size of ngram)
        final int count = 0;
        final List list = new ArrayList();
        List sublist = new ArrayList();
        final NGramEntry[] entries = (NGramEntry[]) ngrams.values().toArray(new NGramEntry[ngrams.size()]);
        for (int i = minLength; i <= maxLength; i++) {
            for (final NGramEntry entrie : entries) {
                if (entrie.getSeq().length() == i) {
                    sublist.add(entrie);
                }
            }
            Collections.sort(sublist);
            if (sublist.size() > MAX_SIZE) {
                sublist = sublist.subList(0, MAX_SIZE);
            }
            list.addAll(sublist);
            sublist.clear();
        }
        for (int i = 0; i < list.size(); i++) {
            final NGramEntry e = (NGramEntry) list.get(i);
            final String line = e.toString() + " " + e.getCount() + "\n";
            os.write(line.getBytes("UTF-8"));
        }
        os.flush();
    }

    // Inherited JavaDoc
    @Override
    public String toString() {

        final StringBuffer s = new StringBuffer().append("NGramProfile: ").append(name).append("\n");

        final Iterator i = getSorted().iterator();

        while (i.hasNext()) {
            final NGramEntry entry = (NGramEntry) i.next();
            s.append("[").append(entry.seq).append("/").append(entry.count).append("/").append(entry.frequency).append("]\n");
        }
        return s.toString();
    }

    /**
     * Add ngrams from a single word in this profile
     * 
     * @param word
     *            is the word to add
     * @param n
     *            is the ngram size
     */
    private void add(final CharSequence cs) {

        if (cs.equals(SEP_CHARSEQ)) {
            return;
        }
        NGramEntry nge = (NGramEntry) ngrams.get(cs);
        if (nge == null) {
            nge = new NGramEntry(cs);
            ngrams.put(cs, nge);
        }
        nge.inc();
    }

    /**
     * Add the last NGrams from the specified word.
     */
    private void add(final QuickStringBuffer word) {
        final int wlen = word.length();
        if (wlen >= minLength) {
            final int max = Math.min(maxLength, wlen);
            for (int i = minLength; i <= max; i++) {
                add(word.subSequence(wlen - i, wlen));
            }
        }
    }

    /**
     * @param word
     * @param n
     *            sequence length
     */
    private void add(final StringBuffer word, final int n) {
        for (int i = 0; i <= word.length() - n; i++) {
            add(word.subSequence(i, i + n));
        }
    }

    /**
     * Normalize the profile (calculates the ngrams frequencies)
     */
    protected void normalize() {

        NGramEntry e = null;
        // List sorted = getSorted();
        Iterator i = ngrams.values().iterator();

        // Calculate ngramcount if not already done
        if (ngramcounts == null) {
            ngramcounts = new int[maxLength + 1];
            while (i.hasNext()) {
                e = (NGramEntry) i.next();
                ngramcounts[e.size()] += e.count;
            }
        }

        i = ngrams.values().iterator();
        while (i.hasNext()) {
            e = (NGramEntry) i.next();
            e.frequency = (float) e.count / (float) ngramcounts[e.size()];
        }
    }

}
