package com.github.rnewson.couchdb.lucene.couchdb;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.codec.binary.Base64;

import com.ericsson.otp.erlang.OtpErlangDecodeException;
import com.ericsson.otp.erlang.OtpErlangList;
import com.ericsson.otp.erlang.OtpErlangLong;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangTuple;
import com.ericsson.otp.erlang.OtpInputStream;

/**
 * This class represents a point-in-time for a couchdb or bigcouch database.
 * 
 * @author robertnewson
 * 
 */
public abstract class UpdateSequence {

	private static class BigCouchUpdateSequence extends UpdateSequence {

		private final String since;
		private final Map<String, Long> vector = new HashMap<String, Long>();

		private BigCouchUpdateSequence(final String encodedVector) {
			this.since = encodedVector;

			final String packedSeqs = encodedVector.split("-", 2)[1];
			final byte[] bytes = new Base64(true).decode(packedSeqs);
			final OtpInputStream stream = new OtpInputStream(bytes);
			try {
				final OtpErlangList list = (OtpErlangList) stream.read_any();
				for (int i = 0, arity = list.arity(); i < arity; i++) {
					final OtpErlangTuple tuple = (OtpErlangTuple) list.elementAt(i);
					final OtpErlangObject node = tuple.elementAt(0);
					final OtpErlangObject range = tuple.elementAt(1);
					final OtpErlangLong node_seq = (OtpErlangLong) tuple.elementAt(2);
					vector.put(node + "-" + range, node_seq.longValue());
				}
			} catch (final OtpErlangDecodeException e) {
				throw new IllegalArgumentException(encodedVector + " not valid.");
			}
		}

		@Override
		public String appendSince(final String url) {
			return url + "&since=" + since;
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

	public static UpdateSequence parseUpdateSequence(final String str) {
		if (str.matches("[0-9]+")) {
			return new CouchDbUpdateSequence(str);
		}
		if (str.matches("[0-9]+-[0-9a-zA-Z_-]+")) {
			return new BigCouchUpdateSequence(str);
		}
		throw new IllegalArgumentException(str + " not recognized.");
	}

	public abstract String appendSince(final String url);

	public abstract boolean isEarlierThan(final UpdateSequence other);

	public abstract boolean isLaterThan(final UpdateSequence other);

}
