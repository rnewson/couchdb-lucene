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

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class PathPartsTest {

    @Test
    public void testSearchPath() {
        final PathParts parts = new PathParts(
                "/local/db1/_design/foo/by_subject");
        assertThat(parts.getKey(), is("local"));
        assertThat(parts.getDatabaseName(), is("db1"));
        assertThat(parts.getDesignDocumentName(), is("_design/foo"));
        assertThat(parts.getViewName(), is("by_subject"));
        assertThat(parts.getCommand(), is(nullValue()));
    }

    @Test
    public void testCommandPath() {
        final PathParts parts = new PathParts(
                "/local/db1/_design/foo/by_subject/_expunge");
        assertThat(parts.getKey(), is("local"));
        assertThat(parts.getDatabaseName(), is("db1"));
        assertThat(parts.getDesignDocumentName(), is("_design/foo"));
        assertThat(parts.getViewName(), is("by_subject"));
        assertThat(parts.getCommand(), is("_expunge"));
    }

    @Test
    public void testCleanupPath() {
        final PathParts parts = new PathParts(
                "/local/db1/_cleanup");
        assertThat(parts.getKey(), is("local"));
        assertThat(parts.getDatabaseName(), is("db1"));
        assertThat(parts.getCommand(), is("_cleanup"));
    }

}
