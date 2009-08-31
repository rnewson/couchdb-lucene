package com.github.rnewson.couchdb.lucene;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.analysis.LowerCaseTokenizer;
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

public final class AnalyzerCache {

    public enum KnownAnalyzer {

        BRAZILIAN {
            @Override
            public Analyzer newAnalyzer() {
                return new BrazilianAnalyzer();
            }
        },
        CHINESE {
            @Override
            public Analyzer newAnalyzer() {
                return new ChineseAnalyzer();
            }
        },
        CJK {
            @Override
            public Analyzer newAnalyzer() {
                return new CJKAnalyzer();
            }
        },
        CZECH {
            @Override
            public Analyzer newAnalyzer() {
                return new CzechAnalyzer();
            }
        },
        DUTCH {
            @Override
            public Analyzer newAnalyzer() {
                return new DutchAnalyzer();
            }
        },
        ENGLISH {
            @Override
            public Analyzer newAnalyzer() {
                return new StandardAnalyzer(Version.LUCENE_CURRENT);
            }
        },
        FRENCH {
            @Override
            public Analyzer newAnalyzer() {
                return new FrenchAnalyzer();
            }
        },
        GERMAN {
            @Override
            public Analyzer newAnalyzer() {
                return new GermanAnalyzer();
            }
        },
        KEYWORD {
            @Override
            public Analyzer newAnalyzer() {
                return new KeywordAnalyzer();
            }
        },
        PORTER {
            @Override
            public Analyzer newAnalyzer() {
                return new PorterStemAnalyzer();
            }
        },
        RUSSIAN {
            @Override
            public Analyzer newAnalyzer() {
                return new RussianAnalyzer();
            }
        },
        SIMPLE {
            @Override
            public Analyzer newAnalyzer() {
                return new SimpleAnalyzer();
            }
        },
        STANDARD {
            @Override
            public Analyzer newAnalyzer() {
                return new StandardAnalyzer(Version.LUCENE_CURRENT);
            }
        },
        THAI {
            @Override
            public Analyzer newAnalyzer() {
                return new ThaiAnalyzer();
            }
        };

        private static final class PorterStemAnalyzer extends Analyzer {
            @Override
            public TokenStream tokenStream(final String fieldName, final Reader reader) {
                return new PorterStemFilter(new LowerCaseTokenizer(reader));
            }
        }

        public abstract Analyzer newAnalyzer();

    }

    private static Map<String, Analyzer> MAP = new HashMap<String, Analyzer>();

    public static Analyzer getAnalyzer(String name) {
        name = name.toUpperCase();
        Analyzer result = MAP.get(name);
        if (result != null) {
            return result;
        }

        result = KnownAnalyzer.valueOf(name).newAnalyzer();

        MAP.put(name, result);
        return result;
    }

}
