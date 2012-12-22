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

import org.apache.lucene.index.TermVectorMapper;
import org.apache.lucene.index.TermVectorOffsetInfo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class JsonTermVectorMapper extends TermVectorMapper {

	private JSONObject result = new JSONObject();
	private JSONObject currentObj;

	@Override
	public void setExpectations(String field, int numTerms,
			boolean storeOffsets, boolean storePositions) {
		currentObj = new JSONObject();
		try {
			result.put(field, currentObj);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void map(String term, int frequency, TermVectorOffsetInfo[] offsets,
			int[] positions) {
		try {
			final JSONObject field = new JSONObject();
			field.put("freq", frequency);
			if (offsets != null) {
				final JSONArray arr = new JSONArray();
				for (int i = 0; i < offsets.length; i++) {
					final JSONArray arr2 = new JSONArray();
					arr2.put(offsets[i].getStartOffset());
					arr2.put(offsets[i].getEndOffset());
					arr.put(arr2);
				}
				field.put("offsets", arr);
			} else {
				field.put("offsets", "null");
			}
			if (positions != null) {
				field.put("positions", positions);
			} else {
				field.put("positions", "null");
			}
			currentObj.put(term, field);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	public JSONObject getObject() {
		return result;
	}

}
