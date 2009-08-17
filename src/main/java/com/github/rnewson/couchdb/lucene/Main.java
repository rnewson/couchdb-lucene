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

/**
 * Entry point for indexing and searching.
 * 
 * @author rnewson
 * 
 */
public final class Main {

    public static void main(final String[] args) throws Exception {
        if (System.getProperty("couchdb.log.dir") == null) {
            System.setProperty("couchdb.log.dir", System.getProperty("java.io.tmpdir"));
        }
        
        if (args.length >= 1 && args[0].equals("-index")) {
            Index.main(args);
            return;
        }

        if (args.length >= 1 && args[0].equals("-search")) {
            Search.main(args);
            return;
        }

        Utils.out(Utils.error("Invoke with -index or -search only."));
        return;
    }

}
