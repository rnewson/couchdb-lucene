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

package com.github.rnewson.couchdb.lucene.util;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;


public class UtilsTest {

    @Test
    public void testSplitOnCommas() {
        assertArrayEquals(new String[]{"foo", "bar"}, Utils.splitOnCommas("foo,bar"));
    }

    @Test
    public void testSplitOnCommasWithEmbeddedCommas() {
        assertArrayEquals(new String[]{"\"fo,o\"", "bar"}, Utils.splitOnCommas("\"fo,o\",bar"));
    }

}
