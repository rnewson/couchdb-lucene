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

package com.github.rnewson.couchdb.lucene;

import org.apache.lucene.search.*;

/**
 * this class converts Query objects to a textual description of the classes
 * used to execute it.
 *
 * @author rnewson
 */
public final class QueryPlan {

    private QueryPlan() {

    }

    /**
     * Produces a string representation of the query classes used for a query.
     *
     * @param query
     * @return
     */
    public static String toPlan(final Query query) {
        final StringBuilder builder = new StringBuilder(300);
        toPlan(builder, query);
        return builder.toString();
    }

    private static void planBooleanQuery(final StringBuilder builder, final BooleanQuery query) {
        for (final BooleanClause clause : query.clauses()) {
            builder.append(clause.getOccur());
            toPlan(builder, clause.getQuery());
        }
    }

    private static void planFuzzyQuery(final StringBuilder builder, final FuzzyQuery query) {
        builder.append(query.getTerm());
        builder.append(",prefixLength=");
        builder.append(query.getPrefixLength());
        builder.append(",maxEdits=");
        builder.append(query.getMaxEdits());
    }

    private static void planPrefixQuery(final StringBuilder builder, final PrefixQuery query) {
        builder.append(query.getPrefix());
    }

    private static void planTermQuery(final StringBuilder builder, final TermQuery query) {
        builder.append(query.getTerm());
    }

    private static void planTermRangeQuery(final StringBuilder builder, final TermRangeQuery query) {
        builder.append(query.getLowerTerm().utf8ToString());
        builder.append(" TO ");
        builder.append(query.getUpperTerm().utf8ToString());
    }

    private static void planWildcardQuery(final StringBuilder builder, final WildcardQuery query) {
        builder.append(query.getTerm());
    }

    private static void planBoostQuery(final StringBuilder builder, final BoostQuery query) {
        toPlan(builder, query.getQuery());
        builder.append(",boost=" + query.getBoost() + ")");
    }

    private static void toPlan(final StringBuilder builder, final Query query) {
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
        } else if (query instanceof BoostQuery) {
            planBoostQuery(builder, (BoostQuery) query);
        } else {
            builder.append(query.getClass());
            builder.append("@");
            builder.append(query);
        }
        builder.append(")");
    }

}
