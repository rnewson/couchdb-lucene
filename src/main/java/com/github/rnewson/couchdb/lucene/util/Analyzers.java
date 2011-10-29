package com.github.rnewson.couchdb.lucene.util;

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

import java.io.Reader;
import java.util.Iterator;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.analysis.LowerCaseTokenizer;
import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.PorterStemFilter;
import org.apache.lucene.analysis.SimpleAnalyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.analysis.br.BrazilianAnalyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.cn.ChineseAnalyzer;
import org.apache.lucene.analysis.cz.CzechAnalyzer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.nl.DutchAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.snowball.SnowballAnalyzer;
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.th.ThaiAnalyzer;
import org.json.JSONException;
import org.json.JSONObject;

public enum Analyzers {

    BRAZILIAN {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new BrazilianAnalyzer(Constants.VERSION);
        }
    },
    CHINESE {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new ChineseAnalyzer();
        }
    },
    CJK {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new CJKAnalyzer(Constants.VERSION);
        }
    },
    CLASSIC {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new ClassicAnalyzer(Constants.VERSION);
        }
    },
    CZECH {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new CzechAnalyzer(Constants.VERSION);
        }
    },
    DUTCH {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new DutchAnalyzer(Constants.VERSION);
        }
    },
    ENGLISH {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new StandardAnalyzer(Constants.VERSION);
        }
    },
    FRENCH {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new FrenchAnalyzer(Constants.VERSION);
        }
    },
    GERMAN {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new GermanAnalyzer(Constants.VERSION);
        }
    },
    KEYWORD {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new KeywordAnalyzer();
        }
    },
    PERFIELD {
        @Override
        public Analyzer newAnalyzer(final String args) throws JSONException {
            final JSONObject json = new JSONObject(args == null ? "{}" : args);
            final Analyzer defaultAnalyzer = Analyzers.getAnalyzer(json.optString(Constants.DEFAULT_FIELD, "standard"));
            final PerFieldAnalyzerWrapper result = new PerFieldAnalyzerWrapper(defaultAnalyzer);
            final Iterator<?> it = json.keys();
            while (it.hasNext()) {
                final String key = it.next().toString();
                if (Constants.DEFAULT_FIELD.equals(key))
                    continue;
                result.addAnalyzer(key, Analyzers.getAnalyzer(json.getString(key)));
            }
            return result;
        }
    },
    PORTER {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new PorterStemAnalyzer();
        }
    },
    RUSSIAN {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new RussianAnalyzer(Constants.VERSION);
        }
    },
    SIMPLE {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new SimpleAnalyzer(Constants.VERSION);
        }
    },
    SNOWBALL {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new SnowballAnalyzer(Constants.VERSION, args);
        }
    },
    STANDARD {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new StandardAnalyzer(Constants.VERSION);
        }
    },
    THAI {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new ThaiAnalyzer(Constants.VERSION);
        }
    },
    WHITESPACE {
        public Analyzer newAnalyzer(final String args) {
            return new WhitespaceAnalyzer(Constants.VERSION);
        }
    };

    private static final class PorterStemAnalyzer extends Analyzer {
        @Override
        public TokenStream tokenStream(final String fieldName, final Reader reader) {
            return new PorterStemFilter(new LowerCaseTokenizer(Constants.VERSION, reader));
        }
    }

    public static Analyzer getAnalyzer(final String str) throws JSONException {
        final String[] parts = str.split(":", 2);
        final String name = parts[0].toUpperCase();
        final String args = parts.length == 2 ? parts[1] : null;
        return Analyzers.valueOf(name).newAnalyzer(args);
    }

    public abstract Analyzer newAnalyzer(final String args) throws JSONException;

}
