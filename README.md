# couchdb-lucene [![Build Status](https://secure.travis-ci.org/rnewson/couchdb-lucene.svg)](http://travis-ci.org/rnewson/couchdb-lucene)

## Version Compatibility
CouchDB-Lucene works with all version of CouchDB from 0.10 upwards.

## Issue Tracking
Issue tracking at [github](http://github.com/rnewson/couchdb-lucene/issues).

## Minimum System Requirements
Java 1.8 is required; Oracle Java 8 or OpenJDK 8 are recommended.

## Build and run couchdb-lucene
If you are on OS X, you might find it easiest to:

```bash
brew install couchdb-lucene
```

1. Install Maven (2 or 3).
2. checkout repository
3. type 'mvn'
4. cd target
5. unzip couchdb-lucene-\<version\>.zip
6. cd couchdb-lucene-\<version\>
7. ./bin/run

The zip file contains all the couchdb-lucene code, dependencies, startup scripts and configuration files you need, so unzip it wherever you wish to install couchdb-lucene.

If you want to run couchdb-lucene on a servlet container like Tomcat, you can build the war file using Maven

```bash
mvn war:war
```

## Configure CouchDB
The following settings are needed in CouchDB's local.ini file in order for it to communicate with couchdb-lucene:

### Proxy handler (for CouchDB versions from 1.1 onward)
```ini
[httpd_global_handlers]
_fti = {couch_httpd_proxy, handle_proxy_req, <<"http://127.0.0.1:5985">>}
```

### Python hook script (for CouchDB versions prior to 1.1)
```ini
[couchdb]
os_process_timeout=60000 ; increase the timeout from 5 seconds.

[external]
fti=/path/to/python /path/to/couchdb-lucene/tools/couchdb-external-hook.py

[httpd_db_handlers]
_fti = {couch_httpd_external, handle_external_req, <<"fti">>}
```

#### Hook options
You can pass options to the python script like so:
```ini
[external]
fti=/path/to/python "/path/to/couchdb-lucene/tools/couchdb-external-hook.py --option-name value"
```

|Option       |Meaning                                                                     |Default Value|
|-------------|----------------------------------------------------------------------------|-------------|
|--remote-host|The hostname of the couchdb-lucene server                                   |localhost    |
|--remote-port|The port of the couchdb-lucene server                                       |5985         |
|--local-key  |The key for the local couchdb instance as known to the couchdb-lucene server|local        |

## Configure couchdb-lucene
couchdb-lucene runs in a single, standalone JVM. As such, you can choose to locate your couchdb-lucene server on a different machine to couchdb if you wish, or keep it on the same machine, it's your call.

## Start couchdb-lucene
To start couchdb-lucene, run:
```bash
bin/run
```

To stop couchdb-lucene, simply kill the Java process.

## Indexing Strategy
### Document Indexing
You must supply a index function in order to enable couchdb-lucene as, by default, nothing will be indexed. To suppress a document from the index, return null. It's more typical to return a single Document object which contains everything you'd like to query and retrieve. You may also return an array of Document objects if you wish.

You may add any number of index views in any number of design documents. All searches will be constrained to documents emitted by the index functions.

Here's an complete example of a design document with couchdb-lucene features:

```json
{
    "_id":"_design/foo",
    "fulltext": {
        "by_subject": {
            "index":"function(doc) { var ret=new Document(); ret.add(doc.subject); return ret }"
        },
        "by_content": {
            "index":"function(doc) { var ret=new Document(); ret.add(doc.content); return ret }"
        }
    }
}
```

Here are some example URL's for the given design document:

### Using the Python hook script
```
http://localhost:5984/database/_fti/_design/foo/by_subject?q=hello
http://localhost:5984/database/_fti/_design/foo/by_content?q=hello
```

### Using the proxy handler
```
http://localhost:5984/_fti/local/database/_design/foo/by_subject?q=hello
http://localhost:5984/_fti/local/database/_design/foo/by_content?q=hello
```

A fulltext object contains multiple index view declarations. An index view consists of:

***analyzer***
  (optional) The analyzer to use

***defaults***
  (optional) The default for numerous indexing options can be overridden here. A full list of options follows.

***index***
  The indexing function itself, documented below.

#### The Defaults Object
The following indexing options can be defaulted:

|name|description|available options|default|
|----|-----------|-----------------|-------|
|field|the field name to index under|user-defined|default|
|type|the type of the field|date, double, float, int, long, string, text|text|
|store|whether the data is stored. The value will be returned in the search result.|yes, no|no|
|boost|Sets the boost factor hits on this field. This value will be multiplied into the score of all hits on this this field of this document.|floating-point value|1.0|

#### String vs Text

There are two supported types that sound equivalent, *string* and *text*, but they are very different. A text field will be tokenized into words and is usually what you expect from a full-text index. A string field is not tokenized, only exact matching will work. The advantage to string fields is that they have a meaningful sort order.

#### The Analyzer Option

Lucene has numerous ways of converting free-form text into tokens, these classes are called Analyzer's. By default, the StandardAnalyzer is used which lower-cases all text, drops common English words ("the", "and", and so on), among other things. This processing might not always suit you, so you can choose from several others by setting the "analyzer" field to one of the following values:

- brazilian
- chinese
- cjk
- czech
- dutch
- english
- french
- german
- keyword
- perfield
- porter
- russian
- simple
- snowball
- standard
- thai
- whitespace
- ngram

##### The Snowball Analyzer
This analyzer requires an extra argument to specify the language (see [here](http://lucene.apache.org/java/3_0_3/api/contrib-snowball/org/apache/lucene/analysis/snowball/SnowballAnalyzer.html) for details):

```json
"analyzer":"snowball:English"
```

Note: the argument is case-sensitive and is passed directly to the `SnowballAnalyzer`'s constructor.

##### The Per-field Analyzer
The "perfield" option lets you use a different analyzer for different fields and is configured as follows:

```json
"analyzer":"perfield:{field_name:\"analyzer_name\"}"
```

Unless overridden, any field name not specified will be handled by the standard analyzer. To change the default, use the special default field name:

```json
"analyzer":"perfield:{default:\"keyword\"}"
```

##### The Ngram Analyzer
The "ngram" analyzer lets you break down the output of any other analyzer into ngrams ("foo" becomes "fo" and "oo").

```json
"analyzer":"ngram:{analyzer:\"simple\",min:2,max:3}"
```

If not specified, the delegated analyzer is "standard" and min and max ngram sizes are 1 and 2 respectively.

##### Configuring additional analyzers

There are many other analyzers included in Lucene and there are also occasions where custom analyzers not included in Lucene are needed. 
There is now support for [configuring additional analyzers](CONFIGURING_ANALYZERS.md) without needing to further modify couchdb-lucene.

#### The Document class
You may construct a new Document instance with:

```js
var doc = new Document();
```

Data may be added to this document with the add method which takes an optional second object argument that can override any of the above default values.

```js
// Add with all the defaults.
doc.add("value");

// Add a numeric field.
doc.add(35, {"type":"int"});

// Add a date field.
doc.add(new Date("1972/1/6 16:05:00"),        {"type":"date"});
doc.add(new Date("January 6, 1972 16:05:00"), {"type":"date"});

// Add a date field (object must be a Date object

// Add a subject field.
doc.add("this is the subject line.", {"field":"subject"});

// Add but ensure it's stored.
doc.add("value", {"store":"yes"});

// Add but don't analyze.
doc.add("don't analyze me", {"index":"not_analyzed"});

// Extract text from the named attachment and index it to a named field
doc.attachment("attachment field", "attachment name");

// log an event (trace, debug, info, warn and error are available)
if (doc.foo) {
  log.info("doc has foo property!");
}
```

#### Example Index Functions
##### Index Everything
```js
function(doc) {
    var ret = new Document();

    function idx(obj) {
	for (var key in obj) {
	    switch (typeof obj[key]) {
	    case 'object':
		idx(obj[key]);
		break;
	    case 'function':
		break;
	    default:
		ret.add(obj[key]);
		break;
	    }
	}
    };

    idx(doc);

    if (doc._attachments) {
	for (var i in doc._attachments) {
	    ret.attachment("default", i);
	}
    }

    return ret;
}
```

##### Index Nothing
```js
function(doc) {
  return null;
}
```

##### Index Select Fields
```js
function(doc) {
  var result = new Document();
  result.add(doc.subject, {"field":"subject", "store":"yes"});
  result.add(doc.content, {"field":"subject"});
  result.add(new Date(), {"field":"indexed_at"});
  return result;
}
```

##### Index Attachments
```js
function(doc) {
  var result = new Document();
  for(var a in doc._attachments) {
    result.attachment("default", a);
  }
  return result;
}
```

##### A More Complex Example
```js
function(doc) {
    var mk = function(name, value, group) {
        var ret = new Document();
        ret.add(value, {"field": group, "store":"yes"});
        ret.add(group, {"field":"group", "store":"yes"});
        return ret;
    };
    if(doc.type != "reference") return null;
    var ret = new Array();
    for(var g in doc.groups) {
        ret.push(mk("library", doc.groups[g].library, g));
        ret.push(mk("method", doc.groups[g].method, g));
        ret.push(mk("target", doc.groups[g].target, g));
    }
    return ret;
}
```

### Attachment Indexing
Couchdb-lucene uses [Apache Tika](http://lucene.apache.org/tika/) to index attachments of the following types, assuming the correct content_type is set in couchdb;

#### Supported Formats
- Excel spreadsheets (application/vnd.ms-excel)
- HTML (text/html)
- Images (image/*)
- Java class files
- Java jar archives
- MP3 (audio/mp3)
- OpenDocument (application/vnd.oasis.opendocument.*)
- Outlook (application/vnd.ms-outlook)
- PDF (application/pdf)
- Plain text (text/plain)
- Powerpoint presentations (application/vnd.ms-powerpoint)
- RTF (application/rtf)
- Visio (application/vnd.visio)
- Word documents (application/msword)
- XML (application/xml)

## Searching with couchdb-lucene
You can perform all types of queries using Lucene's default [query syntax](http://lucene.apache.org/java/3_6_2/queryparsersyntax.html).

### Numeric range queries
In addition to normal text-based range searches (using the "field:[lower TO upper]" syntax), couchdb-lucene also supports numeric range searches for the following types: int, long, float, double and date. The type is specified after the field name, as follows:

|type|example|
|----|-------|
|int|field<int>:[0 TO 100]|
|long|field<long>:[0 TO 100]|
|float|field<float>:[0.0 TO 100.0]|
|double|field<double>:[0.0 TO 100.0]|
|date|field<date>:[from TO to] where from and to match any of these patterns: `"yyyy-MM-dd'T'HH:mm:ssZ"`, `"yyyy-MM-dd'T'HH:mm:ss"`, `"yyyy-MM-ddZ"`, `"yyyy-MM-dd"`, `"yyyy-MM-dd'T'HH:mm:ss.SSSZ"`, `"yyyy-MM-dd'T'HH:mm:ss.SSS"`. So, in order to search for articles published in July, you would issue a following query: `published_at<date>:["2010-07-01T00:00:00"+TO+"2010-07-31T23:59:59"]`|

An example numeric range query for spatial searching.

```
?q=pizza AND lat<double>:[51.4707 TO 51.5224] AND long<double>:[-0.6622 TO -0.5775]
```

### Numeric term queries
Fields indexed with numeric types can still be queried as normal terms, couchdb-lucene just needs to know the type. For example, `?q=age<long>:12` will find all documents where the field called 'age' has a value of 12 (when the field was indexed as "type":"int".

### Search methods
You may use HTTP GET or POST. For POST, use application/x-www-form-urlencoded format.

### Search parameters
The following parameters can be passed for more sophisticated searches:

***analyzer***
  Override the default analyzer used to parse the q parameter

***callback***
  Specify a JSONP callback wrapper. The full JSON result will be prepended with this parameter and also placed with parentheses."

***debug***
  Setting this to true disables response caching (the query is executed every time) and indents the JSON response for readability.

***default_operator***
  Change the default operator for boolean queries. Defaults to "OR", other permitted value is "AND".

***force_json***
  Usually couchdb-lucene determines the Content-Type of its response based on the presence of the Accept header. If Accept contains "application/json", you get "application/json" in the response, otherwise you get "text/plain;charset=utf8". Some tools, like JSONView for FireFox, do not send the Accept header but do render "application/json" responses if received. Setting force_json=true forces all response to "application/json" regardless of the Accept header.

***include_docs***
  whether to include the source docs

***include_fields***
  By default, *all* stored fields are returned with results. Use a comma-separate list of field names with this parameter to refine the response

***highlights***
  Number of highlights to include with results. Default is *0*. This uses the *fast-vector-highlighter* plugin.

***highlight_length***
  Number of characters to include in a highlight row. Default and minimum is *18*.

***limit***
  the maximum number of results to return. Default is *25*.

***q***
  the query to run (e.g, subject:hello). If not specified, the default field is searched. Multiple queries can be supplied, separated by commas; the resulting JSON will be an array of responses.

***skip***
  the number of results to skip

***sort***
  the comma-separated fields to sort on. Prefix with / for ascending order and \ for descending order (ascending is the default if not specified). Type-specific sorting is also available by appending the type between angle brackets (e.g, sort=amount<float>). Supported types are 'float', 'double', 'int', 'long' and 'date'.

***stale=ok***
  If you set the *stale* option to *ok*, couchdb-lucene will not block if the index is not up to date and it will immediately return results. Therefore searches may be faster as Lucene caches important data (especially for sorting). A query without stale=ok will block and use the latest data committed to the index. Unlike CouchDBs stale=ok option for views, couchdb-lucene will trigger an index update unless one is already running.

*All parameters except 'q' are optional.*

### Special Fields
***_id***
  The _id of the document.

### Dublin Core
All Dublin Core attributes are indexed and stored if detected in the attachment. Descriptions of the fields come from the Tika javadocs.


***_dc.contributor***
   An entity responsible for making contributions to the content of the resource.

***_dc.coverage***
  The extent or scope of the content of the resource.

***_dc.creator***
  An entity primarily responsible for making the content of the resource.

***_dc.date***
  A date associated with an event in the life cycle of the resource.

***_dc.description***
  An account of the content of the resource.

***_dc.format***
  Typically, Format may include the media-type or dimensions of the resource.

***_dc.identifier***
  Recommended best practice is to identify the resource by means of a string or number conforming to a formal identification system.

***_dc.language***
  A language of the intellectual content of the resource.

***_dc.modified***
  Date on which the resource was changed.

***_dc.publisher***
  An entity responsible for making the resource available.

***_dc.relation***
  A reference to a related resource.

***_dc.rights***
  Information about rights held in and over the resource.

***_dc.source***
  A reference to a resource from which the present resource is derived.

***_dc.subject***
  The topic of the content of the resource.

***_dc.title***
  A name given to the resource.

***_dc.type***
  The nature or genre of the content of the resource.

### Examples

### Using the Python hook script
```
http://localhost:5984/dbname/_fti/_design/foo/view_name?q=field_name:value
http://localhost:5984/dbname/_fti/_design/foo/view_name?q=field_name:value&sort=other_field
http://localhost:5984/dbname/_fti/_design/foo/view_name?debug=true&sort=billing_size<long>&q=body:document AND customer:[A TO C]
```

### Using the proxy handler
```
http://localhost:5984/_fti/local/dbname/_design/foo/view_name?q=field_name:value
http://localhost:5984/_fti/local/dbname/_design/foo/view_name?q=field_name:value&sort=other_field
http://localhost:5984/_fti/local/dbname/_design/foo/view_name?debug=true&sort=billing_size<long>&q=body:document AND customer:[A TO C]
```

### Search Results Format
The search result contains a number of fields at the top level, in addition to your search results.

***etag***
  An opaque token that reflects the current version of the index. This value is also returned in an ETag header to facilitate HTTP caching.

***fetch_duration***
  The number of milliseconds spent retrieving the documents.

***limit***
  The maximum number of results that can appear.

***q***
  The query that was executed.

***rows***
  The search results array, described below.

***search_duration***
  The number of milliseconds spent performing the search.

***skip***
  The number of initial matches that was skipped.

***total_rows***
  The total number of matches for this query.

### The search results array

The search results arrays consists of zero, one or more objects with the following fields:

***doc***
  The original document from couch, if requested with include_docs=true

***fields***
  All the fields that were stored with this match

***id***
  The unique identifier for this match.

***score***
  The normalized score (0.0-1.0, inclusive) for this match

Here's an example of a JSON response without sorting:

```json
{
  "q": "+content:enron",
  "skip": 0,
  "limit": 2,
  "total_rows": 176852,
  "search_duration": 518,
  "fetch_duration": 4,
  "rows":   [
        {
      "id": "hain-m-all_documents-257.",
      "score": 1.601625680923462
    },
        {
      "id": "hain-m-notes_inbox-257.",
      "score": 1.601625680923462
    }
  ]
}
```

And the same with sorting:

```json
{
  "q": "+content:enron",
  "skip": 0,
  "limit": 3,
  "total_rows": 176852,
  "search_duration": 660,
  "fetch_duration": 4,
  "sort_order":   [
        {
      "field": "source",
      "reverse": false,
      "type": "string"
    },
        {
      "reverse": false,
      "type": "doc"
    }
  ],
  "rows":   [
        {
      "id": "shankman-j-inbox-105.",
      "score": 0.6131107211112976,
      "sort_order":       [
        "enron",
        6
      ]
    },
        {
      "id": "shankman-j-inbox-8.",
      "score": 0.7492915391921997,
      "sort_order":       [
        "enron",
        7
      ]
    },
        {
      "id": "shankman-j-inbox-30.",
      "score": 0.507369875907898,
      "sort_order":       [
        "enron",
        8
      ]
    }
  ]
}
```

#### Content-Type of response
The Content-Type of the response is negotiated via the Accept request header like CouchDB itself. If the Accept header includes "application/json" then that is also the Content-Type of the response. If not, "text/plain;charset=utf-8" is used.

## Fetching information about the index
Calling couchdb-lucene without arguments returns a JSON object with information about the index.

```
http://127.0.0.1:5984/<db>/_fti/_design/foo/<index>
```

returns:

```json
{"current":true,"disk_size":110674,"doc_count":397,"doc_del_count":0,
"fields":["default","number"],"last_modified":"1263066382000",
"optimized":true,"ref_count":2}
```

## Index Maintenance
For optimal query speed you can optimize your indexes. This causes the index to be rewritten into a single segment.

```bash
curl -X POST http://localhost:5984/<db>/_fti/_design/foo/<index>/_optimize
```

If you just want to expunge pending deletes, then call:

```bash
curl -X POST http://localhost:5984/<db>/_fti/_design/foo/<index>/_expunge
```

If you recreate databases or frequently change your fulltext functions, you will probably have old indexes lying around on disk. To remove all of them, call:

```bash
curl -X POST http://localhost:5984/<db>/_fti/_cleanup
```

## Authentication

By default couchdb-lucene does not attempt to authenticate to CouchDB. If you have set CouchDB's require_valid_user to true, you will need to modify couchdb-lucene.ini. Change the url setting to include a valid username and password. e.g, the default setting is:

```ini
[local]
url=http://localhost:5984/
```

Change it to:

```ini
[local]
url=http://foo:bar@localhost:5984/
```

and couchdb-lucene will authenticate to couchdb.

## Other Tricks
A couple of 'expert' options can be set in the couchdb-lucene.ini file;

Leading wildcards are prohibited by default as they perform very poorly most of the time. You can enable them as follows:

```ini
[lucene]
allowLeadingWildcard=true
```

Lucene automatically converts terms to lower case in wildcard situations. You can disable this with:

```ini
[lucene]
lowercaseExpandedTerms=false
```

CouchDB-Lucene will keep your indexes up to date automatically but this consumes resources (network sockets). You can ask CouchDB-Lucene to stop updating an index after a timeout with:

```ini
[lucene]
changes_timeout = 60000
```

