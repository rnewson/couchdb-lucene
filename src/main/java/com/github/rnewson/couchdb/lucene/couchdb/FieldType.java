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

import java.util.Date;

import org.apache.commons.lang.time.DateUtils;
import org.apache.lucene.document.AbstractField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.util.NumericUtils;

public enum FieldType {

    DATE(8, SortField.LONG) {

        @Override
        public NumericField toField(final String name, final Object value, final ViewSettings settings) throws ParseException {
            return field(name, precisionStep, settings).setLongValue(toDate(value));
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper, final boolean inclusive)
                throws ParseException {
            return NumericRangeQuery.newLongRange(name, precisionStep, toDate(lower), toDate(upper), inclusive, inclusive);
        }
        
        @Override
        public Query toTermQuery(final String name, final String text) throws ParseException {
            final long date = toDate(text);
            return new TermQuery(new Term(name, NumericUtils.longToPrefixCoded(date)));
        }

    },
    DOUBLE(8, SortField.DOUBLE) {
        @Override
        public NumericField toField(final String name, final Object value, final ViewSettings settings) {
            return field(name, precisionStep, settings).setDoubleValue(toDouble(value));
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper, final boolean inclusive) {
            return NumericRangeQuery.newDoubleRange(name, precisionStep, toDouble(lower), toDouble(upper), inclusive, inclusive);
        }
        
        @Override
        public Query toTermQuery(final String name, final String text) {
            return new TermQuery(new Term(name, NumericUtils.doubleToPrefixCoded(toDouble(text))));
        }

        private double toDouble(final Object obj) {
        	if (obj instanceof Number) {
        		return ((Number)obj).doubleValue();
        	}
            return Double.parseDouble(obj.toString());
        }

    },
    FLOAT(4, SortField.FLOAT) {
        @Override
        public NumericField toField(final String name, final Object value, final ViewSettings settings) {
            return field(name, 4, settings).setFloatValue(toFloat(value));
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper, final boolean inclusive) {
            return NumericRangeQuery.newFloatRange(name, precisionStep, toFloat(lower), toFloat(upper), inclusive, inclusive);
        }
        
        @Override
        public Query toTermQuery(final String name, final String text) {
            return new TermQuery(new Term(name, NumericUtils.floatToPrefixCoded(toFloat(text))));
        }

        private float toFloat(final Object obj) {
        	if (obj instanceof Number) {
        		return ((Number)obj).floatValue();
        	}
            return Float.parseFloat(obj.toString());
        }
    },
    INT(4, SortField.INT) {
        @Override
        public NumericField toField(final String name, final Object value, final ViewSettings settings) {
            return field(name, 4, settings).setIntValue(toInt(value));
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper, final boolean inclusive) {
            return NumericRangeQuery.newIntRange(name, precisionStep, toInt(lower), toInt(upper), inclusive, inclusive);
        }
        
        @Override
        public Query toTermQuery(final String name, final String text) {
            return new TermQuery(new Term(name, NumericUtils.intToPrefixCoded(toInt(text))));
        }

        private int toInt(final Object obj) {
        	if (obj instanceof Number) {
        		return ((Number)obj).intValue();
        	}
            return Integer.parseInt(obj.toString());
        }

    },
    LONG(8, SortField.LONG) {
        @Override
        public NumericField toField(final String name, final Object value, final ViewSettings settings) {
            return field(name, precisionStep, settings).setLongValue(toLong(value));
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper, final boolean inclusive) {
            return NumericRangeQuery.newLongRange(name, precisionStep, toLong(lower), toLong(upper), inclusive, inclusive);
        }

        private long toLong(final Object obj) {
        	if (obj instanceof Number) {
        		return ((Number)obj).longValue();
        	}
            return Long.parseLong(obj.toString());
        }

        @Override
        public Query toTermQuery(final String name, final String text) {
            return new TermQuery(new Term(name, NumericUtils.longToPrefixCoded(toLong(text))));
        }

    },
    STRING(0, SortField.STRING) {
        @Override
        public Field toField(final String name, final Object value, final ViewSettings settings) {
            return field(name, value, settings);
        }

        @Override
        public Query toRangeQuery(final String name, final String lower, final String upper, final boolean inclusive) {
            final TermRangeQuery result = new TermRangeQuery(name, lower, upper, inclusive, inclusive);
            result.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_AUTO_REWRITE_DEFAULT);
            return result;
        }

        @Override
        public Query toTermQuery(String name, String text) {
            throw new UnsupportedOperationException("toTermQuery is not supported for FieldType.String.");
        }
    };

    private static NumericField field(final String name, final int precisionStep, final ViewSettings settings) {
        return boost(new NumericField(name, precisionStep, settings.getStore(), settings.getIndex().isIndexed()), settings);
    }

    private static Field field(final String name, final Object value, final ViewSettings settings) {
        return boost(new Field(name, value.toString(), settings.getStore(), settings.getIndex(), settings.getTermVector()), settings);
    }

    private static <T extends AbstractField> T boost(final T field, final ViewSettings settings) {
        field.setBoost(settings.getBoost());
        return field;
    }

    public static final String[] DATE_PATTERNS = new String[] { "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-ddZ",
            "yyyy-MM-dd", "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ss.SSS"};

    private final int sortField;

    protected final int precisionStep;

    private FieldType(final int precisionStep, final int sortField) {
        this.precisionStep = precisionStep;
        this.sortField = sortField;
    }

    public abstract AbstractField toField(final String name, final Object value, final ViewSettings settings) throws ParseException;

    public abstract Query toRangeQuery(final String name, final String lower, final String upper, final boolean inclusive)
            throws ParseException;
    
    public abstract Query toTermQuery(final String name, final String text) throws ParseException;

    public final int toSortField() {
        return sortField;
    }

    public static long toDate(final Object obj) throws ParseException {
    	if (obj instanceof Date) {
    		return ((Date)obj).getTime();
    	}
        try {
            return DateUtils.parseDate(obj.toString().toUpperCase(), DATE_PATTERNS).getTime();
        } catch (final java.text.ParseException e) {
            throw new ParseException(e.getMessage());
        }
    }

}
