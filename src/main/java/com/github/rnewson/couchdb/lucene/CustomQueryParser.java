package com.github.rnewson.couchdb.lucene;

import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
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
                return null;
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

}
