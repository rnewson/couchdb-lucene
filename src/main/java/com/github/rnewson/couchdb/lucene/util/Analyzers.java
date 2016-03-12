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
import org.apache.lucene.analysis.AnalyzerWrapper;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.br.BrazilianAnalyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.cn.ChineseAnalyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.LowerCaseTokenizer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.cz.CzechAnalyzer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.nl.DutchAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.snowball.SnowballAnalyzer;
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.th.ThaiAnalyzer;
import org.apache.lucene.analysis.ngram.NGramTokenFilter;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
            final Map<String, Analyzer> analyzers = new HashMap<>();
            final Iterator<?> it = json.keys();
            while (it.hasNext()) {
                final String key = it.next().toString();
                if (Constants.DEFAULT_FIELD.equals(key))
                    continue;
                analyzers.put(key, Analyzers.getAnalyzer(json.getString(key)));
            }
            return new PerFieldAnalyzerWrapper(defaultAnalyzer, analyzers);
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
    },
    NGRAM {
        public Analyzer newAnalyzer(final String args) throws JSONException {
            final JSONObject json = new JSONObject(args == null ? "{}" : args);
            final Analyzer analyzer = Analyzers.getAnalyzer(json.optString("analyzer", "standard"));
            int min = json.optInt("min", NGramTokenFilter.DEFAULT_MIN_NGRAM_SIZE);
            int max = json.optInt("max", NGramTokenFilter.DEFAULT_MAX_NGRAM_SIZE);
            return new NGramAnalyzer(analyzer, min, max);
        }
    };

    private static final class PorterStemAnalyzer extends Analyzer {
        @Override
        protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
            Tokenizer source = new LowerCaseTokenizer(Constants.VERSION, reader);
            return new TokenStreamComponents(source, new PorterStemFilter(source));
        }
    }

    private static final class NGramAnalyzer extends AnalyzerWrapper {
        private final Analyzer analyzer;
        private final int min;
        private final int max;

        public NGramAnalyzer(final Analyzer analyzer, final int min, final int max) {
            this.analyzer = analyzer;
            this.min = min;
            this.max = max;
        }

        @Override
        protected Analyzer getWrappedAnalyzer(final String fieldName) {
            return analyzer;
        }

        @Override
        protected TokenStreamComponents wrapComponents(String fieldName, TokenStreamComponents components) {
            return new TokenStreamComponents(components.getTokenizer(),
                new NGramTokenFilter(Constants.VERSION, components.getTokenStream(),
                    this.min, this.max));
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
