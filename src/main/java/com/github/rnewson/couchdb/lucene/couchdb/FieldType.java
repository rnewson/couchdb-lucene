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
import org.apache.lucene.util.BytesRefBuilder;
import org.apache.lucene.util.NumericUtils;

import java.util.Date;

public enum FieldType {

    DATE(SortField.Type.LONG) {
        @Override
        public void addFields(final String name, final Object value, final ViewSettings settings, final Document to) throws ParseException {
            to.add(boost(new LongPoint(name, toDate(value)), settings));
            to.add(new NumericDocValuesField(name, toDate(value)));
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper,
                                  final boolean lowerInclusive, final boolean upperInclusive)
                throws ParseException {
            return LongPoint.newRangeQuery(name,
                                           lowerInclusive ? toDate(lower) : Math.addExact(toDate(lower), 1),
                                           upperInclusive ? toDate(upper) : Math.addExact(toDate(upper), -1));
        }

        @Override
        public Query toTermQuery(final String name, final String text) throws ParseException {
            return LongPoint.newExactQuery(name, toDate(text));
        }

    },
    DOUBLE(SortField.Type.DOUBLE) {
        @Override
        public void addFields(final String name, final Object value, final ViewSettings settings, final Document to) {
            to.add(boost(new DoublePoint(name, toDouble(value)), settings));
            to.add(new DoubleDocValuesField(name, toDouble(value)));
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper,
                                  final boolean lowerInclusive, final boolean upperInclusive) {
            return DoublePoint.newRangeQuery(name,
                                             lowerInclusive ? toDouble(lower) : Math.nextUp(toDouble(lower)),
                                             upperInclusive ? toDouble(upper) : Math.nextDown(toDouble(upper)));
        }

        @Override
        public Query toTermQuery(final String name, final String text) {
            return DoublePoint.newExactQuery(name, toDouble(text));
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
        public void addFields(final String name, final Object value, final ViewSettings settings, final Document to) {
            to.add(boost(new FloatPoint(name, toFloat(value)), settings));
            to.add(new FloatDocValuesField(name, toFloat(value)));
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper,
                                  final boolean lowerInclusive, final boolean upperInclusive) {
            return FloatPoint.newRangeQuery(name,
                                            lowerInclusive ? toFloat(lower) : Math.nextUp(toFloat(lower)),
                                            upperInclusive ? toFloat(upper) : Math.nextDown(toFloat(upper)));
        }

        @Override
        public Query toTermQuery(final String name, final String text) {
            return FloatPoint.newExactQuery(name, toFloat(text));
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
        public void addFields(final String name, final Object value, final ViewSettings settings, final Document to) {
            to.add(boost(new IntPoint(name, toInt(value)), settings));
            to.add(new NumericDocValuesField(name, toInt(value)));
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper,
                                  final boolean lowerInclusive, final boolean upperInclusive) {
            return IntPoint.newRangeQuery(name,
                                          lowerInclusive ? toInt(lower) : Math.addExact(toInt(lower), 1),
                                          upperInclusive ? toInt(upper) : Math.addExact(toInt(upper), -1));
        }

        @Override
        public Query toTermQuery(final String name, final String text) {
            return IntPoint.newExactQuery(name, toInt(text));
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
        public void addFields(final String name, final Object value, final ViewSettings settings, final Document to) {
            to.add(boost(new LongPoint(name, toLong(value)), settings));
            to.add(new NumericDocValuesField(name, toLong(value)));
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper,
                                  final boolean lowerInclusive, final boolean upperInclusive) {
            return LongPoint.newRangeQuery(name,
                                           lowerInclusive ? toLong(lower) : Math.addExact(toLong(lower), 1),
                                           upperInclusive ? toLong(upper) : Math.addExact(toLong(upper), -1));
        }

        @Override
        public Query toTermQuery(final String name, final String text) {
            return LongPoint.newExactQuery(name, toLong(text));
        }

        private long toLong(final Object obj) {
            if (obj instanceof Number) {
                return ((Number) obj).longValue();
            }
            return Long.parseLong(obj.toString());
        }

    },
    STRING(SortField.Type.STRING) {
        @Override
        public void addFields(final String name, final Object value, final ViewSettings settings, final Document to) {
            to.add(boost(new StringField(name, value.toString(), settings.getStore()), settings));
            to.add(new SortedDocValuesField(name, new BytesRef(value.toString())));
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper,
                                  final boolean lowerInclusive, final boolean upperInclusive) {
            return TermRangeQuery.newStringRange(name, lower, upper,
                    lowerInclusive, upperInclusive);
        }

        @Override
        public Query toTermQuery(String name, String text) {
            throw new UnsupportedOperationException("toTermQuery is not supported for FieldType.String.");
        }
    },
    TEXT(null) {
        @Override
        public void addFields(final String name, final Object value, final ViewSettings settings, final Document to) {
            to.add(boost(new TextField(name, value.toString(), settings.getStore()), settings));
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper,
                                  final boolean lowerInclusive, final boolean upperInclusive) {
            throw new UnsupportedOperationException("toRangeQuery is not supported for TEXT");
        }

        @Override
        public Query toTermQuery(String name, String text) {
            throw new UnsupportedOperationException("toTermQuery is not supported for TEXT");
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

    public abstract void addFields(final String name, final Object value, final ViewSettings settings, final Document to) throws ParseException;

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
