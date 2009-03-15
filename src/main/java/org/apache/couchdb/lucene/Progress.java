package org.apache.couchdb.lucene;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

public final class Progress {

	private static final String FILENAME = "couchdb.status";

	private final Directory dir;

	private final Map<String, Long> progress = new HashMap<String, Long>();

	public Progress(final Directory dir) {
		this.dir = dir;
	}

	public long getProgress(final String dbname) {
		final Long result = progress.get(dbname);
		return result == null ? 0 : result;
	}

	public void load() throws IOException {
		if (dir.fileExists(FILENAME) == false) {
			progress.clear();
			return;
		}

		final IndexInput in = dir.openInput(FILENAME);
		try {
			progress.clear();
			final int size = in.readVInt();
			for (int i = 0; i < size; i++) {
				final String dbname = in.readString();
				final long update_seq = in.readVLong();
				setProgress(dbname, update_seq);
			}
		} finally {
			in.close();
		}
	}

	public void save() throws IOException {
		final String tmp = "couchdb.new";
		final IndexOutput out = dir.createOutput(tmp);
		try {
			out.writeVInt(progress.size());
			for (final Entry<String, Long> entry : progress.entrySet()) {
				out.writeString(entry.getKey());
				out.writeVLong(entry.getValue());
			}
			out.close();
			dir.sync(tmp);
			dir.renameFile(tmp, FILENAME);
		} catch (final IOException e) {
			dir.deleteFile(tmp);
			throw e;
		} finally {
			out.close();
		}
	}

	public void setProgress(final String dbname, final long progress) {
		this.progress.put(dbname, progress);
	}

}
