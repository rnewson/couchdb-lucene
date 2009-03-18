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

public final class Log {

	public static void outlog(final String fmt, final Object... args) {
		System.out.print("{\"log\":\"");
		System.out.printf(fmt, args);
		System.out.println("\"}");
	}

	public static void errlog(final String fmt, final Object... args) {
		System.err.printf(fmt, args);
		System.err.println();
	}

	public static void outlog(final Exception e) {
		outlog("%s", e.getMessage());
		e.printStackTrace(System.out);
	}

	public static void errlog(final Exception e) {
		errlog("%s", e.getMessage());
		e.printStackTrace();
	}

}
