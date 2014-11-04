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

package com.github.rnewson.couchdb.lucene.couchdb;

import org.apache.commons.lang.time.DateUtils;
import org.apache.lucene.document.*;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;

import java.util.Date;

public enum FieldType {

    DATE(SortField.Type.LONG) {
        @Override
        public LongField toField(final String name, final Object value, final ViewSettings settings) throws ParseException {
            return boost(new LongField(name, toDate(value), settings.getStore()), settings);
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper,
                                  final boolean lowerInclusive, final boolean upperInclusive)
                throws ParseException {
            return NumericRangeQuery.newLongRange(name, toDate(lower), toDate(upper),
                    lowerInclusive, upperInclusive);
        }

        @Override
        public Query toTermQuery(final String name, final String text) throws ParseException {
            final long date = toDate(text);
            final BytesRef ref = new BytesRef();
            NumericUtils.longToPrefixCoded(date, 0, ref);
            return new TermQuery(new Term(name, ref));
        }

    },
    DOUBLE(SortField.Type.DOUBLE) {
        @Override
        public DoubleField toField(final String name, final Object value, final ViewSettings settings) {
            return boost(new DoubleField(name, toDouble(value), settings.getStore()), settings);
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper,
                                  final boolean lowerInclusive, final boolean upperInclusive) {
            return NumericRangeQuery.newDoubleRange(name, toDouble(lower), toDouble(upper),
                    lowerInclusive, upperInclusive);
        }

        @Override
        public Query toTermQuery(final String name, final String text) {
            final long asLong = NumericUtils.doubleToSortableLong(toDouble(text));
            final BytesRef ref = new BytesRef();
            NumericUtils.longToPrefixCoded(asLong, 0, ref);
            return new TermQuery(new Term(name, ref));
        }

        private double toDouble(final Object obj) {
            if (obj instanceof Number) {
                return ((Number) obj).doubleValue();
            }
            return Double.parseDouble(obj.toString());
        }

    },
    FLOAT(SortField.Type.FLOAT) {
        @Override
        public FloatField toField(final String name, final Object value, final ViewSettings settings) {
            return boost(new FloatField(name, toFloat(value), settings.getStore()), settings);
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper,
                                  final boolean lowerInclusive, final boolean upperInclusive) {
            return NumericRangeQuery.newFloatRange(name, toFloat(lower), toFloat(upper),
                    lowerInclusive, upperInclusive);
        }

        @Override
        public Query toTermQuery(final String name, final String text) {
            final int asInt = NumericUtils.floatToSortableInt(toFloat(text));
            final BytesRef ref = new BytesRef();
            NumericUtils.intToPrefixCoded(asInt, 0, ref);
            return new TermQuery(new Term(name, ref));
        }

        private float toFloat(final Object obj) {
            if (obj instanceof Number) {
                return ((Number) obj).floatValue();
            }
            return Float.parseFloat(obj.toString());
        }
    },
    INT(SortField.Type.INT) {
        @Override
        public IntField toField(final String name, final Object value, final ViewSettings settings) {
            return boost(new IntField(name, toInt(value), settings.getStore()), settings);
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper,
                                  final boolean lowerInclusive, final boolean upperInclusive) {
            return NumericRangeQuery.newIntRange(name, toInt(lower), toInt(upper),
                    lowerInclusive, upperInclusive);
        }

        @Override
        public Query toTermQuery(final String name, final String text) {
            final BytesRef ref = new BytesRef();
            NumericUtils.intToPrefixCoded(toInt(text), 0, ref);
            return new TermQuery(new Term(name, ref));
        }

        private int toInt(final Object obj) {
            if (obj instanceof Number) {
                return ((Number) obj).intValue();
            }
            return Integer.parseInt(obj.toString());
        }

    },
    LONG(SortField.Type.LONG) {
        @Override
        public LongField toField(final String name, final Object value, final ViewSettings settings) {
            return boost(new LongField(name, toLong(value), settings.getStore()), settings);
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper,
                                  final boolean lowerInclusive, final boolean upperInclusive) {
            return NumericRangeQuery.newLongRange(name, toLong(lower), toLong(upper),
                    lowerInclusive, upperInclusive);
        }

        private long toLong(final Object obj) {
            if (obj instanceof Number) {
                return ((Number) obj).longValue();
            }
            return Long.parseLong(obj.toString());
        }

        @Override
        public Query toTermQuery(final String name, final String text) {
            final BytesRef ref = new BytesRef();
            NumericUtils.longToPrefixCoded(toLong(text), 0, ref);
            return new TermQuery(new Term(name, ref));
        }

    },
    STRING(SortField.Type.STRING) {
        @Override
        public Field toField(final String name, final Object value, final ViewSettings settings) {
            return boost(new Field(name, value.toString(), settings.getStore(), settings.getIndex(),
                    settings.getTermVector()), settings);
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper,
                                  final boolean lowerInclusive, final boolean upperInclusive) {
            final TermRangeQuery result = TermRangeQuery.newStringRange(name, lower, upper,
                    lowerInclusive, upperInclusive);
            result.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_AUTO_REWRITE_DEFAULT);
            return result;
        }

        @Override
        public Query toTermQuery(String name, String text) {
            throw new UnsupportedOperationException("toTermQuery is not supported for FieldType.String.");
        }
    };

    private static <T extends Field> T boost(final T field, final ViewSettings settings) {
        field.setBoost(settings.getBoost());
        return field;
    }

    public static final String[] DATE_PATTERNS = new String[]{"yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-ddZ",
            "yyyy-MM-dd", "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ss.SSS"};

    private final SortField.Type type;


    private FieldType(final SortField.Type type) {
        this.type = type;
    }

    public abstract Field toField(final String name, final Object value, final ViewSettings settings) throws ParseException;

    public abstract Query toRangeQuery(final String name, final String lower, final String upper,
                                       final boolean lowerInclusive, final boolean upperInclusive)
            throws ParseException;

    public abstract Query toTermQuery(final String name, final String text) throws ParseException;

    public final SortField.Type toType() {
        return type;
    }

    public static long toDate(final Object obj) throws ParseException {
        if (obj instanceof Date) {
            return ((Date) obj).getTime();
        }
        try {
            return DateUtils.parseDate(obj.toString().toUpperCase(), DATE_PATTERNS).getTime();
        } catch (final java.text.ParseException e) {
            throw new ParseException(e.getMessage());
        }
    }

}
