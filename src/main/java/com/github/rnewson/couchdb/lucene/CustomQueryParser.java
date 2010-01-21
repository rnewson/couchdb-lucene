package com.github.rnewson.couchdb.lucene;

/**
 * Copyright 2009 Robert Newson
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

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.util.Version;

/**
 * Custom query parser that uses NumericFieldQuery where appropriate.
 * 
 * @author rnewson
 * 
 */
public final class CustomQueryParser extends QueryParser {

    private static String[] DATE_PATTERNS = new String[] { "yyyy-MM-dd'T'HH:mm:ssZZ", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-ddZZ",
            "yyyy-MM-dd" };

    public CustomQueryParser(final Version matchVersion, final String f, final Analyzer a) {
        super(matchVersion, f, a);
    }

    public static Sort toSort(final String sort) throws ParseException {
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

                final TypedField typedField = new TypedField(tmp);
                sort_fields[i] = new SortField(typedField.getName(), typedField.getType().asSortField(), reverse);
            }
            return new Sort(sort_fields);
        }
    }

    public static String toString(final SortField[] sortFields) {
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

    @Override
    protected Query getRangeQuery(final String field, final String part1, final String part2, final boolean inclusive)
            throws ParseException {
        final TypedField typedField = new TypedField(field);

        switch (typedField.getType()) {
        case STRING:
            return newRangeQuery(field, part1, part2, inclusive);
        case INT:
            return NumericRangeQuery.newIntRange(typedField.getName(), 4, Integer.parseInt(part1), Integer.parseInt(part2), inclusive, inclusive);
        case LONG:
            return NumericRangeQuery.newLongRange(typedField.getName(), 4, Long.parseLong(part1), Long.parseLong(part2), inclusive, inclusive);
        case FLOAT:
            return NumericRangeQuery
                    .newFloatRange(typedField.getName(), 4, Float.parseFloat(part1), Float.parseFloat(part2), inclusive, inclusive);
        case DOUBLE:
            return NumericRangeQuery.newDoubleRange(typedField.getName(), 4, Double.parseDouble(part1), Double.parseDouble(part2), inclusive,
                    inclusive);
        case DATE:
            return NumericRangeQuery.newLongRange(typedField.getName(), 8, date(part1), date(part2), inclusive, inclusive);
        default:
            throw new ParseException("Unknown type " + typedField);
        }
    }

    private long date(final String str) throws ParseException {
        try {
            return DateUtils.parseDate(str.toUpperCase(), DATE_PATTERNS).getTime();
        } catch (DateParseException e) {
            throw new ParseException(e.getMessage());
        }
    }

}
