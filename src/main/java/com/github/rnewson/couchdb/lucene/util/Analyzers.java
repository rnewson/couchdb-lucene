package com.github.rnewson.couchdb.lucene.util;

import java.io.Reader;

import net.sf.json.JSONObject;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.analysis.LowerCaseTokenizer;
import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.PorterStemFilter;
import org.apache.lucene.analysis.SimpleAnalyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.br.BrazilianAnalyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.cn.ChineseAnalyzer;
import org.apache.lucene.analysis.cz.CzechAnalyzer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.nl.DutchAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.th.ThaiAnalyzer;
import org.apache.lucene.util.Version;

public enum Analyzers {

    BRAZILIAN {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new BrazilianAnalyzer(VERSION);
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
            return new CJKAnalyzer(VERSION);
        }
    },
    CZECH {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new CzechAnalyzer(VERSION);
        }
    },
    DUTCH {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new DutchAnalyzer(VERSION);
        }
    },
    ENGLISH {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new StandardAnalyzer(VERSION);
        }
    },
    FRENCH {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new FrenchAnalyzer(VERSION);
        }
    },
    GERMAN {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new GermanAnalyzer(VERSION);
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
        public Analyzer newAnalyzer(final String args) {
            final JSONObject json = JSONObject.fromObject(args == null ? "{}" : args);
            final Analyzer defaultAnalyzer = Analyzers.getAnalyzer(json.optString(Constants.DEFAULT_FIELD, "standard"));
            final PerFieldAnalyzerWrapper result = new PerFieldAnalyzerWrapper(defaultAnalyzer);
            for (final Object obj : json.keySet()) {
                final String key = obj.toString();
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
            return new RussianAnalyzer(VERSION);
        }
    },
    SIMPLE {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new SimpleAnalyzer();
        }
    },
    STANDARD {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new StandardAnalyzer(Version.LUCENE_CURRENT);
        }
    },
    THAI {
        @Override
        public Analyzer newAnalyzer(final String args) {
            return new ThaiAnalyzer(VERSION);
        }
    };

    private static final Version VERSION = Version.LUCENE_30;

    private static final class PorterStemAnalyzer extends Analyzer {
        @Override
        public TokenStream tokenStream(final String fieldName, final Reader reader) {
            return new PorterStemFilter(new LowerCaseTokenizer(reader));
        }
    }

    public static Analyzer getAnalyzer(final String str) {
        final String[] parts = str.split(":", 2);
        final String name = parts[0].toUpperCase();
        final String args = parts.length == 2 ? parts[1] : null;
        return Analyzers.valueOf(name).newAnalyzer(args);
    }

    public abstract Analyzer newAnalyzer(final String args);

}
