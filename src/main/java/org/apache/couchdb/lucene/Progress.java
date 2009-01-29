package org.apache.couchdb.lucene;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class Progress {

	private final Map<String, Long> progress = new HashMap<String, Long>();

	private File dir;

	public Progress(final File dir) {
		this.dir = dir;
	}

	public void load() throws IOException {
		final File dest = new File(dir, "indexing.progress");
		if (dest.exists() == false) {
			progress.clear();
			return;
		}
		final FileInputStream in = new FileInputStream(dest);
		final DataInputStream din = new DataInputStream(in);
		try {
			progress.clear();
			final int size = din.readInt();
			for (int i = 0; i < size; i++) {
				final String dbname = din.readUTF();
				final long v = din.readLong();
				setProgress(dbname, v);
			}
		} finally {
			din.close();
		}
	}

	public void save() throws IOException {
		final File tmp = new File(dir, "indexing.new");
		final File dest = new File(dir, "indexing.progress");

		final FileOutputStream out = new FileOutputStream(tmp);
		final DataOutputStream dout = new DataOutputStream(out);
		try {
			dout.writeInt(progress.size());
			for (final Entry<String, Long> entry : progress.entrySet()) {
				dout.writeUTF(entry.getKey());
				dout.writeLong(entry.getValue());
			}
			dout.flush();
			out.getFD().sync();
			out.close();
			tmp.renameTo(dest);
		} catch (final IOException e) {
			tmp.delete();
			throw e;
		} finally {
			out.close();
		}
	}

	public void setProgress(final String dbname, final long progress) {
		this.progress.put(dbname, progress);
	}

	public long getProgress(final String dbname) {
		final Long result = progress.get(dbname);
		return result == null ? -1 : result;
	}

}
