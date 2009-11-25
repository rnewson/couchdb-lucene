package com.github.rnewson.couchdb.lucene;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.WildcardQuery;

/**
 * this class converts Query objects to a textual description of the classes
 * used to execute it.
 * 
 * @author robertnewson
 * 
 */
public class QueryPlan {

    /**
     * Produces a string representation of the query classes used for a query.
     * 
     * @param query
     * @return
     */
    public String toPlan(final Query query) {
        final StringBuilder builder = new StringBuilder(300);
        toPlan(builder, query);
        return builder.toString();
    }

    private void planBooleanQuery(final StringBuilder builder, final BooleanQuery query) {
        for (final BooleanClause clause : query.getClauses()) {
            builder.append(clause.getOccur());
            toPlan(builder, clause.getQuery());
        }
    }

    private void planFuzzyQuery(final StringBuilder builder, final FuzzyQuery query) {
        builder.append(query.getTerm());
        builder.append(",prefixLength=");
        builder.append(query.getPrefixLength());
        builder.append(",minSimilarity=");
        builder.append(query.getMinSimilarity());
    }

    private void planNumericRangeQuery(final StringBuilder builder, final NumericRangeQuery query) {
        builder.append(query.getMin());
        builder.append(" TO ");
        builder.append(query.getMax());
        builder.append(" AS ");
        builder.append(query.getMin().getClass().getSimpleName());
    }

    private void planPrefixQuery(final StringBuilder builder, final PrefixQuery query) {
        builder.append(query.getPrefix());
    }

    private void planTermQuery(final StringBuilder builder, final TermQuery query) {
        builder.append(query.getTerm());
    }

    private void planTermRangeQuery(final StringBuilder builder, final TermRangeQuery query) {
        builder.append(query.getLowerTerm());
        builder.append(" TO ");
        builder.append(query.getUpperTerm());
    }

    private void planWildcardQuery(final StringBuilder builder, final WildcardQuery query) {
        builder.append(query.getTerm());
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
            planFuzzyQuery(builder, (FuzzyQuery) query);
        } else if (query instanceof NumericRangeQuery) {
            planNumericRangeQuery(builder, (NumericRangeQuery) query);
        }
        builder.append(",boost=" + query.getBoost() + ")");
    }

}
