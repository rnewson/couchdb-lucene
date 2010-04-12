package com.github.rnewson.couchdb.lucene.couchdb;

/**
 * Copyright 2010 Robert Newson
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

import org.apache.commons.lang.time.DateUtils;
import org.apache.lucene.document.AbstractField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermRangeQuery;

public enum FieldType {

    DATE(8, SortField.LONG) {

        @Override
        public NumericField toField(final String name, final String value, final ViewSettings settings) throws ParseException {
            return field(name, precisionStep, settings).setLongValue(toDate(value));
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper, final boolean inclusive)
                throws ParseException {
            return NumericRangeQuery.newLongRange(name, precisionStep, toDate(lower), toDate(upper), inclusive, inclusive);
        }

    },
    DOUBLE(8, SortField.DOUBLE) {
        @Override
        public NumericField toField(final String name, final String value, final ViewSettings settings) {
            return field(name, precisionStep, settings).setDoubleValue(toDouble(value));
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper, final boolean inclusive) {
            return NumericRangeQuery.newDoubleRange(name, precisionStep, toDouble(lower), toDouble(upper), inclusive, inclusive);
        }

        private double toDouble(final String str) {
            return Double.parseDouble(str);
        }

    },
    FLOAT(4, SortField.FLOAT) {
        @Override
        public NumericField toField(final String name, final String value, final ViewSettings settings) {
            return field(name, 4, settings).setFloatValue(toFloat(value));
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper, final boolean inclusive) {
            return NumericRangeQuery.newFloatRange(name, precisionStep, toFloat(lower), toFloat(upper), inclusive, inclusive);
        }

        private float toFloat(final String str) {
            return Float.parseFloat(str);
        }
    },
    INT(4, SortField.INT) {
        @Override
        public NumericField toField(final String name, final String value, final ViewSettings settings) {
            return field(name, 4, settings).setIntValue(toInt(value));
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper, final boolean inclusive) {
            return NumericRangeQuery.newIntRange(name, precisionStep, toInt(lower), toInt(upper), inclusive, inclusive);
        }

        private int toInt(final String str) {
            return Integer.parseInt(str);
        }

    },
    LONG(8, SortField.LONG) {
        @Override
        public NumericField toField(final String name, final String value, final ViewSettings settings) {
            return field(name, precisionStep, settings).setLongValue(toLong(value));
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper, final boolean inclusive) {
            return NumericRangeQuery.newLongRange(name, precisionStep, toLong(lower), toLong(upper), inclusive, inclusive);
        }

        private long toLong(final String str) {
            return Long.parseLong(str);
        }

    },
    STRING(0, SortField.STRING) {
        @Override
        public Field toField(final String name, final String value, final ViewSettings settings) {
            return new Field(name, value, settings.getStore(), settings.getIndex());
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper, final boolean inclusive) {
            final TermRangeQuery result = new TermRangeQuery(name, lower, upper, inclusive, inclusive);
            result.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_AUTO_REWRITE_DEFAULT);
            return result;
        }
    };

    private static NumericField field(final String name, final int precisionStep, final ViewSettings settings) {
        return new NumericField(name, precisionStep, settings.getStore(), settings.getIndex().isIndexed());
    }

    public static final String[] DATE_PATTERNS = new String[] { "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-ddZ",
            "yyyy-MM-dd" };

    private final int sortField;

    protected final int precisionStep;

    private FieldType(final int precisionStep, final int sortField) {
        this.precisionStep = precisionStep;
        this.sortField = sortField;
    }

    public abstract AbstractField toField(final String name, final String value, final ViewSettings settings) throws ParseException;

    public abstract Query toRangeQuery(final String name, final String lower, final String upper, final boolean inclusive)
            throws ParseException;

    public final int toSortField() {
        return sortField;
    }

    public static long toDate(final String str) throws ParseException {
        try {
            return DateUtils.parseDate(str.toUpperCase(), DATE_PATTERNS).getTime();
        } catch (final java.text.ParseException e) {
            throw new ParseException(e.getMessage());
        }
    }

}
