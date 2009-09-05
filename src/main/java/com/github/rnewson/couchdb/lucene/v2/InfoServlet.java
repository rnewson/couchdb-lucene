package com.github.rnewson.couchdb.lucene.v2;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexReader.FieldOption;
import org.apache.lucene.store.Directory;

public class InfoServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final LuceneHolder holder;

    InfoServlet(final LuceneHolder holder) {
        this.holder = holder;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final IndexReader reader = holder.borrowReader();
        final JSONObject json = new JSONObject();
        try {
            json.put("current", reader.isCurrent());
            json.put("disk_size", size(reader.directory()));
            json.put("doc_count", reader.numDocs());
            json.put("doc_del_count", reader.numDeletedDocs());
            final JSONArray fields = new JSONArray();
            for (final Object field : reader.getFieldNames(FieldOption.INDEXED)) {
                if (((String) field).startsWith("_"))
                    continue;
                fields.add(field);
            }
            json.put("fields", fields);
            json.put("last_modified", IndexReader.lastModified(reader.directory()));
            json.put("optimized", reader.isOptimized());
            json.put("ref_count", reader.getRefCount());

            final JSONObject info = new JSONObject();
            info.put("code", 200);
            info.put("json", json);
        } finally {
            holder.returnReader(reader);
        }

        resp.setContentType(Constants.CONTENT_TYPE);
        final Writer writer = resp.getWriter();
        try {
            writer.write(json.toString());
        } finally {
            writer.close();
        }
    }

    private static long size(final Directory dir) throws IOException {
        long result = 0;
        for (final String name : dir.listAll()) {
            result += dir.fileLength(name);
        }
        return result;
    }

}
