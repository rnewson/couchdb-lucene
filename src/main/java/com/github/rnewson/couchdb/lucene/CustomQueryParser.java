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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static Pattern NUMERIC_RANGE_PATTERN = Pattern.compile("^(\\w+)(<\\w+>)?$");

    public CustomQueryParser(final Version matchVersion, final String f, final Analyzer a) {
        super(matchVersion, f, a);
    }

    public static Sort toSort(final String sort) {
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
    protected Query getRangeQuery(final String fieldAndType, final String part1, final String part2, final boolean inclusive)
            throws ParseException {
        final Matcher matcher = NUMERIC_RANGE_PATTERN.matcher(fieldAndType);

        if (!matcher.matches()) {
            throw new ParseException("Field name '" + fieldAndType + "' not recognized.");
        }

        final String field = matcher.group(1);
        final String type = matcher.group(2) == null ? "<string>" : matcher.group(2);

        if ("<string>".equals(type)) {
            return newRangeQuery(field, part1, part2, inclusive);
        }

        if ("<int>".equals(type)) {
            return NumericRangeQuery.newIntRange(field, 4, Integer.parseInt(part1), Integer.parseInt(part2), inclusive, inclusive);
        }

        if ("<long>".equals(type)) {
            return NumericRangeQuery.newLongRange(field, 4, Long.parseLong(part1), Long.parseLong(part2), inclusive, inclusive);
        }

        if ("<float>".equals(type)) {
            return NumericRangeQuery
                    .newFloatRange(field, 4, Float.parseFloat(part1), Float.parseFloat(part2), inclusive, inclusive);
        }

        if ("<double>".equals(type)) {
            return NumericRangeQuery.newDoubleRange(field, 4, Double.parseDouble(part1), Double.parseDouble(part2), inclusive,
                    inclusive);
        }

        if ("<date>".equals(type)) {
            return NumericRangeQuery.newLongRange(field, 8, date(part1), date(part2), inclusive, inclusive);
        }

        throw new ParseException("Unrecognized type '" + type + "'");
    }

    private long date(final String str) throws ParseException {
        try {
            return DateUtils.parseDate(str.toUpperCase(), DATE_PATTERNS).getTime();
        } catch (DateParseException e) {
            throw new ParseException(e.getMessage());
        }
    }

}
