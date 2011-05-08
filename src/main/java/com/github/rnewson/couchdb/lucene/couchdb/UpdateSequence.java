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

public final class UpdateSequence {

	public static final UpdateSequence BOTTOM = new UpdateSequence("0");

	private long seq;
	private Map<String, Long> vector;
	private final String asString;

	public UpdateSequence(final String seq) {
		this.asString = seq;

		if (seq.matches("[0-9]+")) {
			this.seq = Long.parseLong(seq);
			return;
		}

		if (seq.matches("[0-9]+-[0-9a-zA-Z_-]+")) {
			final String packedSeqs = seq.split("-", 2)[1];
			final byte[] bytes = new Base64(true).decode(packedSeqs);
			final OtpInputStream stream = new OtpInputStream(bytes);
			try {
				final OtpErlangList list = (OtpErlangList) stream.read_any();
				this.vector = new HashMap<String, Long>();
				for (int i = 0, arity = list.arity(); i < arity; i++) {
					final OtpErlangTuple tuple = (OtpErlangTuple) list
							.elementAt(i);
					final OtpErlangObject node = tuple.elementAt(0);
					final OtpErlangObject range = tuple.elementAt(1);
					final OtpErlangLong node_seq = (OtpErlangLong) tuple
							.elementAt(2);
					vector.put(node + "-" + range, node_seq.longValue());
				}
			} catch (final OtpErlangDecodeException e) {
				throw new IllegalArgumentException(seq + " not valid.");
			}
			return;
		}

		throw new IllegalArgumentException(seq + " not recognized.");
	}

	public boolean isEarlierThan(final UpdateSequence other) {
		if (this == BOTTOM) {
			return true;
		}

		if (vector == null && other.vector == null) {
			return this.seq < other.seq;
		} else if (vector != null && other.vector != null) {
			final Iterator<Entry<String, Long>> it = this.vector.entrySet()
					.iterator();
			while (it.hasNext()) {
				final Entry<String, Long> entry = it.next();
				final Long otherValue = other.vector.get(entry.getKey());
				if (otherValue != null && otherValue >= entry.getValue()) {
					return false;
				}
			}
			return true;
		}
		throw new IllegalArgumentException(other + " is not compatible.");
	}

	public String toString() {
		return asString;
	}

}
