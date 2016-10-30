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

package com.github.rnewson.couchdb.lucene.couchdb;

import com.ericsson.otp.erlang.*;
import org.apache.commons.codec.binary.Base64;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class represents a point-in-time for a couchdb or bigcouch database.
 *
 * @author robertnewson
 */
public abstract class UpdateSequence {

    private static class BigCouchUpdateSequence extends UpdateSequence {

        private final String since;
        private final Map<String, Long> vector = new HashMap<>();

        private BigCouchUpdateSequence(final String encodedVector, final String packedSeqs) {
            this.since = encodedVector;

            final byte[] bytes = new Base64(true).decode(packedSeqs);
            final OtpInputStream stream = new OtpInputStream(bytes);
            try {
                final OtpErlangList list = (OtpErlangList) stream.read_any();
                for (int i = 0, arity = list.arity(); i < arity; i++) {
                    final OtpErlangTuple tuple = (OtpErlangTuple) list.elementAt(i);
                    final OtpErlangObject node = tuple.elementAt(0);
                    final OtpErlangObject range = tuple.elementAt(1);
                    final OtpErlangObject seq_obj = tuple.elementAt(2);
                    final OtpErlangLong node_seq;
                    if (seq_obj instanceof OtpErlangLong) {
                        node_seq = (OtpErlangLong) seq_obj;
                    } else if (seq_obj instanceof OtpErlangTuple) {
                        node_seq = (OtpErlangLong) ((OtpErlangTuple)seq_obj).elementAt(0);
                    } else {
                        throw new IllegalArgumentException("could not decode seq");
                    }
                    vector.put(node + "-" + range, node_seq.longValue());
                }
            } catch (final OtpErlangDecodeException e) {
                throw new IllegalArgumentException(encodedVector + " not valid.");
            }
        }

        @Override
        public String appendSince(final String url) {
            try {
                return url + "&since=" + URLEncoder.encode(since, "US-ASCII");
            } catch (UnsupportedEncodingException e) {
                throw new Error("US-ASCII inexplicably missing.");
            }
        }

        @Override
        public boolean isEarlierThan(final UpdateSequence other) {
            if (other == START) {
                return false;
            }

            if (other instanceof BigCouchUpdateSequence) {
                final BigCouchUpdateSequence otherBigCouch = (BigCouchUpdateSequence) other;
                final Iterator<Entry<String, Long>> it = this.vector.entrySet().iterator();
                while (it.hasNext()) {
                    final Entry<String, Long> entry = it.next();
                    final Long otherValue = otherBigCouch.vector.get(entry.getKey());
                    if (otherValue != null && entry.getValue() < otherValue) {
                        return true;
                    }
                }
                return false;
            }

            throw new IllegalArgumentException(other + " is not compatible.");
        }

        @Override
        public boolean isLaterThan(final UpdateSequence other) {
            if (other == START) {
                return true;
            }

            if (other instanceof BigCouchUpdateSequence) {
                final BigCouchUpdateSequence otherBigCouch = (BigCouchUpdateSequence) other;
                final Iterator<Entry<String, Long>> it = this.vector.entrySet().iterator();
                while (it.hasNext()) {
                    final Entry<String, Long> entry = it.next();
                    final Long otherValue = otherBigCouch.vector.get(entry.getKey());
                    if (otherValue != null && entry.getValue() > otherValue) {
                        return true;
                    }
                }
                return false;
            }

            throw new IllegalArgumentException(other + " is not compatible.");
        }

        @Override
        public String toString() {
            return since;
        }
    }

    private static class CouchDbUpdateSequence extends UpdateSequence {
        private final long seq;

        private CouchDbUpdateSequence(final String encodedIntegral) {
            this.seq = Long.parseLong(encodedIntegral);
        }

        @Override
        public String appendSince(final String url) {
            return url + "&since=" + seq;
        }

        @Override
        public boolean isEarlierThan(final UpdateSequence other) {
            if (other == START) {
                return false;
            }

            if (other instanceof CouchDbUpdateSequence) {
                return this.seq < ((CouchDbUpdateSequence) other).seq;
            }

            throw new IllegalArgumentException(other + " is not compatible.");
        }

        @Override
        public boolean isLaterThan(final UpdateSequence other) {
            if (other == START) {
                return true;
            }

            if (other instanceof CouchDbUpdateSequence) {
                return this.seq > ((CouchDbUpdateSequence) other).seq;
            }

            throw new IllegalArgumentException(other + " is not compatible.");
        }

        @Override
        public String toString() {
            return Long.toString(seq);
        }

    }

    private static class StartOfUpdateSequence extends UpdateSequence {

        @Override
        public String appendSince(final String url) {
            return url;
        }

        @Override
        public boolean isEarlierThan(final UpdateSequence other) {
            return true;
        }

        @Override
        public boolean isLaterThan(final UpdateSequence other) {
            return false;
        }

        @Override
        public String toString() {
            return "start";
        }

    }

    public static final UpdateSequence START = new StartOfUpdateSequence();

    private static Pattern BC3 = Pattern.compile("[0-9]+-([0-9a-zA-Z_-]+)");
    private static Pattern BC4 = Pattern.compile("\\[[0-9]+\\s*,\\s*\"([0-9a-zA-Z_-]+)\"\\]");

    public static UpdateSequence parseUpdateSequence(final String str) {
        if (str.matches("[0-9]+")) {
            return new CouchDbUpdateSequence(str);
        }
        String packedSeqs;
        if ((packedSeqs = extractPackedSeqs(BC3, str)) != null) {
            return new BigCouchUpdateSequence(str, packedSeqs);
        }
        if ((packedSeqs = extractPackedSeqs(BC4, str)) != null) {
            return new BigCouchUpdateSequence(str, packedSeqs);
        }
        throw new IllegalArgumentException(str + " not recognized.");
    }

    private static String extractPackedSeqs(final Pattern p, final String str) {
        final Matcher m = p.matcher(str);
        if (m.matches()) {
            return m.group(1);
        }
        return null;
    }

    public abstract String appendSince(final String url);

    public abstract boolean isEarlierThan(final UpdateSequence other);

    public abstract boolean isLaterThan(final UpdateSequence other);

}
