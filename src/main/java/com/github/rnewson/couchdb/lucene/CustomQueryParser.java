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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.util.Version;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.github.rnewson.couchdb.lucene.couchdb.FieldType;

/**
 * Custom query parser that uses NumericFieldQuery where appropriate.
 * 
 * @author rnewson
 * 
 */
public final class CustomQueryParser extends QueryParser {

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
				final SortField sortField;
				if ("_score".equals(tmp)) {
					sortField = new SortField(null, SortField.SCORE, reverse);
				} else if ("_doc".equals(tmp)) {
					sortField = new SortField(null, SortField.DOC, reverse);						
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
            col.put("type", type);
            result.put(col);
        }
        return result;
    }

    @Override
    protected Query getRangeQuery(final String field, final String lower, final String upper, final boolean inclusive)
            throws ParseException {
        return new TypedField(field).toRangeQuery(lower, upper, inclusive);
    }

    @Override
    protected Query getFieldQuery(final String field, final String queryText, final boolean quoted)
        throws ParseException {
        final TypedField typedField = new TypedField(field);
        if (typedField.getType() == FieldType.STRING) {
            return super.getFieldQuery(field, queryText, quoted);
        }
        return typedField.toTermQuery(queryText);
    }

}
