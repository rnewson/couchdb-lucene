package com.github.rnewson.couchdb.lucene;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermRangeQuery;

/**
 * Custom query parser that uses NumericFieldQuery where appropriate.
 * 
 * @author robertnewson
 * 
 */
public final class CustomQueryParser {

    private QueryParser delegate;

    public CustomQueryParser(final QueryParser delegate) {
        this.delegate = delegate;
    }

    public Query parse(final String query) throws ParseException {
        return fixup(delegate.parse(query));
    }

    private Query fixup(final Query query) {
        if (query instanceof BooleanQuery) {
            final BooleanQuery booleanQuery = (BooleanQuery) query;
            for (final BooleanClause clause : booleanQuery.getClauses()) {
                clause.setQuery(fixup(clause.getQuery()));
            }
        } else if (query instanceof TermRangeQuery) {
            final TermRangeQuery termRangeQuery = (TermRangeQuery) query;
            final String field = termRangeQuery.getField();
            final Object lower = fixup(termRangeQuery.getLowerTerm());
            final Object upper = fixup(termRangeQuery.getUpperTerm());
            final boolean includesLower = termRangeQuery.includesLower();
            final boolean includesUpper = termRangeQuery.includesUpper();

            // Sanity check.
            if (lower.getClass() != upper.getClass()) {
                return query;
            }

            if (lower instanceof String) {
                return termRangeQuery;
            }
            if (lower instanceof Float) {
                return NumericRangeQuery.newFloatRange(field, 4, (Float) lower, (Float) upper, includesLower, includesUpper);
            }
            if (lower instanceof Double) {
                return NumericRangeQuery.newDoubleRange(field, 8, (Double) lower, (Double) upper, includesLower, includesUpper);
            }
            if (lower instanceof Long) {
                return NumericRangeQuery.newLongRange(field, 8, (Long) lower, (Long) upper, includesLower, includesUpper);
            }
            if (lower instanceof Integer) {
                return NumericRangeQuery.newIntRange(field, 4, (Integer) lower, (Integer) upper, includesLower, includesUpper);
            }
        }
        return query;
    }

    private Object fixup(final String value) {
        if (value.matches("\\d+\\.\\d+f")) {
            return Float.parseFloat(value);
        }
        if (value.matches("\\d+\\.\\d+")) {
            return Double.parseDouble(value);
        }
        if (value.matches("\\d+[lL]")) {
            return Long.parseLong(value.substring(0, value.length() - 1));
        }
        if (value.matches("\\d+")) {
            return Integer.parseInt(value);
        }
        return String.class;
    }

    public Sort toSort(final String sort) {
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

    public String toString(final SortField[] sortFields) {
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

}
