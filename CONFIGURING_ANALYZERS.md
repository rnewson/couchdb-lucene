# Configuring Analyzers

There are many other analyzers included in Lucene and there are occasions where custom analyzers are needed. 
There is now support for configuring additional analyzers without needing to further modify couchdb-lucene. 
This extension is a loose adaptation of the [eXist-db/Lucene integration](https://github.com/eXist-db/exist/blob/develop/extensions/indexes/lucene/src/org/exist/indexing/lucene/AnalyzerConfig.java)

The basic approach is to refer to the analyzer by its java class name, e.g., `org.apache.lucene.analysis.ar.ArabicAnalyzer` or `io.bdrc.lucene.bo.TibetanAnalyzer`
and specify any parameters that need to be supplied to the analyzer constructor, such as stop words or exclusion words lists and the like.

Recall that the current analyzer configuration specifies a couchdb-lucene specific name and optional parameters as in:
``` json
"analyzer":"simple"
```
or

```json
"analyzer":"snowball:English"
```
The analyzer configuration extension uses a json object to specify the analyzer java class and the parameters needed for the analyzer constructor:

```json
"analyzer":
    { "class": "org.apache.lucene.analysis.ar.ArabicAnalyzer",
      "params": [ 
          { "name":"stopwords",
            "type":"set",
            "value": [ "آب", "أربع", "ألا" ] },
          { "name": "stemExclusionSet",
            "type":"set",
            "value": [ "قتول", "قتل" ] } ]
    }
```

The parameter `name` field is optional and is not used other than to provide documentation. The `type` field is one of:

- `string`
- `set`
- `int`
- `boolean`
- `file`

The `set` type corresponds to Lucene `org.apache.lucene.analysis.CharArraySet` which is used in many analyzers to supply sets of stopwords and the like.

The `file` type corresponds to `java.io.FileReader` and is used in some Analyzers to supply initialization information that is too cumbersome to be supplied directly in parameters. It is the responsibility of the Analyzer to close the file to prevent resource leaks.

If the `type` field is not present it defaults to `string`.

Most analyzers provide a nullary constructor so a minimal analyzer configuration is like:

```json
"analyzer": { "class": "org.apache.lucene.analysis.hy.ArmenianAnalyzer" }
```

or

```json
"analyzer": { "class": "org.apache.lucene.analysis.nl.DutchAnalyzer",
              "params": [] }
```

The `SmartChineseAnalyzer` can be configured as follows:

```json
"analyzer":
    { "class": "org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer",
      "params": [
          { "name": "useDefaultStopWords",
            "type": "boolean",
            "value": true } ]
    }
```

All of the examples so far refer to analyzers that are found in the Lucene distribution that is included with couchdb-lucene. All that is needed to add custom analyzers is to put the analyzer class and any dependencies (packaged in a jar typically) on the classpath of couchdb-lucene. A simple way of doing this is to put a jar with the custom analyzers in the `./lib` directory of the couchdb-lucene install. For example, suppose that there is a custom analyzer, `io.bdrc.lucene.bo.TibetanAnalyzer` with associated filters and tokenizer in a jar `io.bdrc.lucene.bo.Tibetan.jar`, then putting the jar in `$COUCHDB-LUCENE_HOME/lib/` will ensure that the TibetanAnalyzer is on the classpath for couchdb-lucene. Then the following could be used to configure couchdb-lucene to use the TibetanAnalyzer:

```json
"analyzer":
    { "class": "io.bdrc.lucene.bo.TibetanAnalyzer",
      "params": [
          { "name": "headWords",
            "type": "file",
            "value": "/usr/local/analyzers/bo/headWords.txt" } ]
    }
```

The set of possible parameter types is not exhaustive, but for analyzers that have constructor parameters not included among the types available (e.g., `org.apache.lucene.analysis.ja.JapaneseAnalyzer`), it is sufficient to build a wrapper analyzer that retrieves information from a file and builds the necessary parameters and then calls the appropriate constructor.

The analyzer configuration extension may be used in the `perfield` and `ngram` analyzer configurations as well as with the `analyzer` parameter in a search request:

```json
"analyzer": 
    "perfield:{
        default:\"keyword\",
        lang_bo:{\"class\":\"io.bdrc.lucene.bo.TibetanAnalyzer\"},
        lang_sa:{\"class\":\"org.apache.lucene.analysis.hi.HindiAnalyzer\"}
    }"
```

The above illustrates that extension configurations can be mixed with the current couchdb-lucene configurations.

The extension also permits using json notation in place of the current analyzer syntax:

```json
"analyzer": 
    { "perfield":
        {"default": "keyword",
         "lang_bo": {"class": "io.bdrc.lucene.bo.TibetanAnalyzer"},
         "lang_sa": {"class": "org.apache.lucene.analysis.hi.HindiAnalyzer"}
        } 
    }
```

or

```json
"analyzer": { "german": {} }
```

or

```json
"analyzer": { "cjk": "" }
```

or

```json
"analyzer": { "snowball": "English" }
```

or

```json
"analyzer": { "ngram": { "analyzer": "simple", "min": 2, "max": 3 } }
```