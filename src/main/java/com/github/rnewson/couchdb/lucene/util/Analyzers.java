package com.github.rnewson.couchdb.lucene.util;

import java.io.Reader;

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

public enum Analyzers {

    BRAZILIAN {
        @Override
        public Analyzer newAnalyzer() {
            return new BrazilianAnalyzer(VERSION);
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
            return new CJKAnalyzer(VERSION);
        }
    },
    CZECH {
        @Override
        public Analyzer newAnalyzer() {
            return new CzechAnalyzer(VERSION);
        }
    },
    DUTCH {
        @Override
        public Analyzer newAnalyzer() {
            return new DutchAnalyzer(VERSION);
        }
    },
    ENGLISH {
        @Override
        public Analyzer newAnalyzer() {
            return new StandardAnalyzer(VERSION);
        }
    },
    FRENCH {
        @Override
        public Analyzer newAnalyzer() {
            return new FrenchAnalyzer(VERSION);
        }
    },
    GERMAN {
        @Override
        public Analyzer newAnalyzer() {
            return new GermanAnalyzer(VERSION);
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
            return new RussianAnalyzer(VERSION);
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

    public static Analyzer getAnalyzer(final String name) {
        return Analyzers.valueOf(name.toUpperCase()).newAnalyzer();
    }

    public abstract Analyzer newAnalyzer();

}
