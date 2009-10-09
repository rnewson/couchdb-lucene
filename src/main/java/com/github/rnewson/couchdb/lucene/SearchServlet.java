package com.github.rnewson.couchdb.lucene;

import static com.github.rnewson.couchdb.lucene.ServletUtils.getBooleanParameter;
import static com.github.rnewson.couchdb.lucene.ServletUtils.getIntParameter;
import static com.github.rnewson.couchdb.lucene.ServletUtils.getParameter;
import static java.lang.Math.max;
import static java.lang.Math.min;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.WildcardQuery;

import com.github.rnewson.couchdb.lucene.LuceneGateway.SearcherCallback;
import com.github.rnewson.couchdb.lucene.util.Analyzers;
import com.github.rnewson.couchdb.lucene.util.StopWatch;

/**
 * Perform queries against local indexes.
 * 
 * @author rnewson
 * 
 */
public final class SearchServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final State state;

    SearchServlet(final State state) {
        this.state = state;
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        if (req.getParameter("q") == null) {
            resp.sendError(400, "Missing q attribute.");
            return;
        }

        final ViewSignature sig = state.locator.lookup(req);
        if (sig == null) {
            resp.sendError(400, "Unknown index.");
            return;
        }

        final boolean staleOk = "ok".equals(req.getParameter("stale"));
        final boolean debug = getBooleanParameter(req, "debug");
        final boolean rewrite_query = getBooleanParameter(req, "rewrite_query");

        final String body = state.lucene.withSearcher(sig, staleOk, new SearcherCallback<String>() {
            public String callback(final IndexSearcher searcher, final String etag) throws IOException {
                // Check for 304 - Not Modified.
                if (!debug && etag.equals(req.getHeader("If-None-Match"))) {
                    resp.setStatus(304);
                    return null;
                }

                // Parse query.
                final Analyzer analyzer = Analyzers.getAnalyzer(getParameter(req, "analyzer", "standard"));
                final QueryParser parser = new QueryParser(Constants.DEFAULT_FIELD, analyzer);

                final Query q;
                try {
                    q = parser.parse(req.getParameter("q"));
                } catch (final ParseException e) {
                    resp.sendError(400, "Bad query syntax.");
                    return null;
                }

                final JSONObject json = new JSONObject();
                json.put("q", q.toString());
                if (debug) {
                    json.put("plan", toPlan(q));
                }
                json.put("etag", etag);

                if (rewrite_query) {
                    final Query rewritten_q = q.rewrite(searcher.getIndexReader());
                    json.put("rewritten_q", rewritten_q.toString());

                    final JSONObject freqs = new JSONObject();

                    final Set<Term> terms = new HashSet<Term>();
                    rewritten_q.extractTerms(terms);
                    for (final Object term : terms) {
                        final int freq = searcher.docFreq((Term) term);
                        freqs.put(term, freq);
                    }
                    json.put("freqs", freqs);
                } else {
                    // Perform the search.
                    final TopDocs td;
                    final StopWatch stopWatch = new StopWatch();

                    final boolean include_docs = getBooleanParameter(req, "include_docs");
                    final int limit = getIntParameter(req, "limit", 25);
                    final Sort sort = toSort(req.getParameter("sort"));
                    final int skip = getIntParameter(req, "skip", 0);

                    if (sort == null) {
                        td = searcher.search(q, null, skip + limit);
                    } else {
                        td = searcher.search(q, null, skip + limit, sort);
                    }
                    stopWatch.lap("search");

                    // Fetch matches (if any).
                    final int max = max(0, min(td.totalHits - skip, limit));
                    final JSONArray rows = new JSONArray();
                    final String[] fetch_ids = new String[max];
                    for (int i = skip; i < skip + max; i++) {
                        final Document doc = searcher.doc(td.scoreDocs[i].doc);
                        final JSONObject row = new JSONObject();
                        final JSONObject fields = new JSONObject();

                        // Include stored fields.
                        for (Object f : doc.getFields()) {
                            Field fld = (Field) f;

                            if (!fld.isStored())
                                continue;
                            String name = fld.name();
                            String value = fld.stringValue();
                            if (value != null) {
                                if ("_id".equals(name)) {
                                    row.put("id", value);
                                } else {
                                    if (!fields.has(name)) {
                                        fields.put(name, value);
                                    } else {
                                        final Object obj = fields.get(name);
                                        if (obj instanceof String) {
                                            final JSONArray arr = new JSONArray();
                                            arr.add((String) obj);
                                            arr.add(value);
                                            fields.put(name, arr);
                                        } else {
                                            assert obj instanceof JSONArray;
                                            ((JSONArray) obj).add(value);
                                        }
                                    }
                                }
                            }
                        }

                        if (!Float.isNaN(td.scoreDocs[i].score)) {
                            row.put("score", td.scoreDocs[i].score);
                        }
                        // Include sort order (if any).
                        if (td instanceof TopFieldDocs) {
                            final FieldDoc fd = (FieldDoc) ((TopFieldDocs) td).scoreDocs[i];
                            row.put("sort_order", fd.fields);
                        }
                        // Fetch document (if requested).
                        if (include_docs) {
                            fetch_ids[i - skip] = doc.get("_id");
                        }
                        if (fields.size() > 0) {
                            row.put("fields", fields);
                        }
                        rows.add(row);
                    }
                    // Fetch documents (if requested).
                    if (include_docs && fetch_ids.length > 0) {
                        final JSONArray fetched_docs = state.couch.getDocs(sig.getDatabaseName(), fetch_ids).getJSONArray("rows");
                        for (int i = 0; i < max; i++) {
                            rows.getJSONObject(i).put("doc", fetched_docs.getJSONObject(i).getJSONObject("doc"));
                        }
                    }
                    stopWatch.lap("fetch");

                    json.put("skip", skip);
                    json.put("limit", limit);
                    json.put("total_rows", td.totalHits);
                    json.put("search_duration", stopWatch.getElapsed("search"));
                    json.put("fetch_duration", stopWatch.getElapsed("fetch"));
                    // Include sort info (if requested).
                    if (td instanceof TopFieldDocs) {
                        json.put("sort_order", SearchServlet.toString(((TopFieldDocs) td).fields));
                    }
                    json.put("rows", rows);
                }

                Utils.setResponseContentTypeAndEncoding(req, resp);

                // Cache-related headers.
                resp.setHeader("ETag", etag);
                resp.setHeader("Cache-Control", "must-revalidate");

                // Format response body.
                final String callback = req.getParameter("callback");
                if (callback != null) {
                    return String.format("%s(%s)", callback, json);
                } else {
                    return json.toString(debug ? 2 : 0);
                }
            }
        });

        // Write response if we have one.
        if (body != null) {
            final Writer writer = resp.getWriter();
            try {
                writer.write(body);
            } finally {
                writer.close();
            }
        }
    }

    private static Sort toSort(final String sort) {
        if (sort == null) {
            return null;
        } else {
            final String[] split = sort.split(",");
            final SortField[] sort_fields = new SortField[split.length];
            for (int i = 0; i < split.length; i++) {
                String tmp = split[i];
                final boolean reverse = tmp.charAt(0) == '\\';
                // Strip sort order character.
                if (tmp.charAt(0) == '\\' || tmp.charAt(0) == '/') {
                    tmp = tmp.substring(1);
                }
                final boolean has_type = tmp.indexOf(':') != -1;
                if (!has_type) {
                    sort_fields[i] = new SortField(tmp, SortField.STRING, reverse);
                } else {
                    final String field = tmp.substring(0, tmp.indexOf(':'));
                    final String type = tmp.substring(tmp.indexOf(':') + 1);
                    int type_int = SortField.STRING;
                    if ("int".equals(type)) {
                        type_int = SortField.INT;
                    } else if ("float".equals(type)) {
                        type_int = SortField.FLOAT;
                    } else if ("double".equals(type)) {
                        type_int = SortField.DOUBLE;
                    } else if ("long".equals(type)) {
                        type_int = SortField.LONG;
                    } else if ("date".equals(type)) {
                        type_int = SortField.LONG;
                    } else if ("string".equals(type)) {
                        type_int = SortField.STRING;
                    }
                    sort_fields[i] = new SortField(field, type_int, reverse);
                }
            }
            return new Sort(sort_fields);
        }
    }

    private static String toString(final SortField[] sortFields) {
        final JSONArray result = new JSONArray();
        for (final SortField field : sortFields) {
            final JSONObject col = new JSONObject();
            col.element("field", field.getField());
            col.element("reverse", field.getReverse());

            final String type;
            switch (field.getType()) {
            case SortField.DOC:
                type = "doc";
                break;
            case SortField.SCORE:
                type = "score";
                break;
            case SortField.INT:
                type = "int";
                break;
            case SortField.LONG:
                type = "long";
                break;
            case SortField.BYTE:
                type = "byte";
                break;
            case SortField.CUSTOM:
                type = "custom";
                break;
            case SortField.DOUBLE:
                type = "double";
                break;
            case SortField.FLOAT:
                type = "float";
                break;
            case SortField.SHORT:
                type = "short";
                break;
            case SortField.STRING:
                type = "string";
                break;
            default:
                type = "unknown";
                break;
            }
            col.element("type", type);
            result.add(col);
        }
        return result.toString();
    }

    /**
     * Produces a string representation of the query classes used for a query.
     * 
     * @param query
     * @return
     */
    private String toPlan(final Query query) {
        final StringBuilder builder = new StringBuilder(300);
        toPlan(builder, query);
        return builder.toString();
    }

    private void toPlan(final StringBuilder builder, final Query query) {
        builder.append(query.getClass().getSimpleName());
        builder.append("(");
        if (query instanceof TermQuery) {
            planTermQuery(builder, (TermQuery) query);
        } else if (query instanceof BooleanQuery) {
            planBooleanQuery(builder, (BooleanQuery) query);
        } else if (query instanceof TermRangeQuery) {
            planTermRangeQuery(builder, (TermRangeQuery) query);
        } else if (query instanceof PrefixQuery) {
            planPrefixQuery(builder, (PrefixQuery) query);
        } else if (query instanceof WildcardQuery) {
            planWildcardQuery(builder, (WildcardQuery) query);
        } else if (query instanceof FuzzyQuery) {
            planFuzzyQuery(builder, (FuzzyQuery)query);
        }
        builder.append(",boost=" + query.getBoost() + ")");
    }

    private void planFuzzyQuery(final StringBuilder builder, final FuzzyQuery query) {
        builder.append(query.getTerm());
        builder.append(",prefixLength=");
        builder.append(query.getPrefixLength());
        builder.append(",minSimilarity=");
        builder.append(query.getMinSimilarity());
    }

    private void planWildcardQuery(final StringBuilder builder, final WildcardQuery query) {
            builder.append(query.getTerm());
    }

    private void planPrefixQuery(final StringBuilder builder, final PrefixQuery query) {
        builder.append(query.getPrefix());
    }

    private void planTermRangeQuery(final StringBuilder builder, final TermRangeQuery query) {
        builder.append(query.getLowerTerm());
        builder.append(" TO ");
        builder.append(query.getUpperTerm());
    }

    private void planBooleanQuery(final StringBuilder builder, final BooleanQuery query) {
        for (final BooleanClause clause : query.getClauses()) {
            builder.append(clause.getOccur());
            toPlan(builder, clause.getQuery());
        }
    }

    private void planTermQuery(final StringBuilder builder, final TermQuery query) {
        builder.append(query.getTerm());
    }

}
