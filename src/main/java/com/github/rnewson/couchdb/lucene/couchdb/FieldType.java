package com.github.rnewson.couchdb.lucene.couchdb;

import java.util.Date;

import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.lucene.document.AbstractField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.search.SortField;

public enum FieldType {

    DATE {
        private String[] patterns = new String[] { "yyyy-MM-dd'T'HH:mm:ssZZ", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-ddZZ", "yyyy-MM-dd" };

        @Override
        public NumericField asField(String name, String value, ViewSettings settings) {
            Date date;
            try {
                date = DateUtils.parseDate(value.toUpperCase(), patterns);
            } catch (final DateParseException e) {
                throw new NumberFormatException();
            }
            return field(name, 8, settings).setLongValue(date.getTime());
        }

        @Override
        public int asSortField() {
            return SortField.LONG;
        }
    },
    DOUBLE {
        @Override
        public NumericField asField(String name, String value, ViewSettings settings) {
            return field(name, 8, settings).setDoubleValue(Double.parseDouble(value));
        }

        @Override
        public int asSortField() {
            return SortField.DOUBLE;
        }
    },
    FLOAT {
        @Override
        public NumericField asField(String name, String value, ViewSettings settings) {
            return field(name, 4, settings).setFloatValue(Float.parseFloat(value));
        }

        @Override
        public int asSortField() {
            return SortField.FLOAT;
        }
    },
    INT {
        @Override
        public NumericField asField(String name, String value, ViewSettings settings) {
            return field(name, 4, settings).setIntValue(Integer.parseInt(value));
        }

        @Override
        public int asSortField() {
            return SortField.INT;
        }
    },
    LONG {
        @Override
        public NumericField asField(String name, String value, ViewSettings settings) {
            return field(name, 8, settings).setLongValue(Long.parseLong(value));
        }

        @Override
        public int asSortField() {
            return SortField.LONG;
        }
    },
    STRING {
        @Override
        public Field asField(String name, String value, ViewSettings settings) {
            return new Field(name, value, settings.getStore(), settings.getIndex());
        }

        @Override
        public int asSortField() {
            return SortField.STRING;
        }
    };

    public abstract int asSortField();

    public abstract AbstractField asField(final String name, final String value, final ViewSettings settings);

    private static NumericField field(final String name, final int precisionStep, final ViewSettings settings) {
        return new NumericField(name, precisionStep, settings.getStore(), settings.getIndex().isIndexed());
    }

}
