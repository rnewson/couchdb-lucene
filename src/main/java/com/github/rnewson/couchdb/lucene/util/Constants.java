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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.util.Version;

public final class Constants {

    private Constants() {
        throw new InstantiationError("This class is not supposed to be instantiated.");
    }

    public static final Version VERSION = Version.LUCENE_4_10_2;

    public static final Analyzer ANALYZER = new StandardAnalyzer(VERSION);

    public static final String APPLICATION_JSON = "application/json";

    public static final String DEFAULT_FIELD = "default";

}
