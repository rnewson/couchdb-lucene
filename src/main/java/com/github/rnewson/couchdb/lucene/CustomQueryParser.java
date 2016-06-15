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

import com.github.rnewson.couchdb.lucene.couchdb.FieldType;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Custom query parser that uses NumericFieldQuery where appropriate.
 *
 * @author rnewson
 */
public final class CustomQueryParser extends QueryParser {

    public CustomQueryParser(final String f, final Analyzer a) {
        super(f, a);
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
                final SortField sortField;
                if ("_score".equals(tmp)) {
                    sortField = new SortField(null, SortField.Type.SCORE, reverse);
                } else if ("_doc".equals(tmp)) {
                    sortField = new SortField(null, SortField.Type.DOC, reverse);
                } else {
                    final TypedField typedField = new TypedField(tmp);
                    sortField = new SortField(typedField.getName(), typedField
                            .toSortField(), reverse);
                }
                sort_fields[i] = sortField;
            }
            return new Sort(sort_fields);
        }
    }

    public static JSONArray toJSON(final SortField[] sortFields) throws JSONException {
        final JSONArray result = new JSONArray();
        for (final SortField field : sortFields) {
            final JSONObject col = new JSONObject();
            col.put("field", field.getField());
            col.put("reverse", field.getReverse());

            final String type;
            switch (field.getType()) {
                case DOC:
                    type = "doc";
                    break;
                case SCORE:
                    type = "score";
                    break;
                case INT:
                    type = "int";
                    break;
                case LONG:
                    type = "long";
                    break;
                case CUSTOM:
                    type = "custom";
                    break;
                case DOUBLE:
                    type = "double";
                    break;
                case FLOAT:
                    type = "float";
                    break;
                case STRING:
                    type = "string";
                    break;
                default:
                    type = "unknown";
                    break;
            }
            col.put("type", type);
            result.put(col);
        }
        return result;
    }

    @Override
    protected Query getRangeQuery(final String field, final String lower, final String upper,
                                  final boolean lowerInclusive, final boolean upperInclusive)
            throws ParseException {
        return new TypedField(field).toRangeQuery(lower, upper, lowerInclusive, upperInclusive);
    }

    @Override
    protected Query getFieldQuery(final String field, final String queryText, final boolean quoted)
            throws ParseException {
        final TypedField typedField = new TypedField(field);
        if (typedField.getType() == FieldType.TEXT) {
            return super.getFieldQuery(field, queryText, quoted);
        }
        return typedField.toTermQuery(queryText);
    }

}
