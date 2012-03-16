[![Build Status](https://secure.travis-ci.org/rnewson/couchdb-lucene.png)](http://travis-ci.org/rnewson/couchdb-lucene)

<h1>Version Compatibility</h1>

CouchDB-Lucene works with all version of CouchDB from 0.10 upwards.

<h1>Breaking Changes</h1>

<ul>
<li>couchdb-lucene 0.5.x and higher runs as a standalone daemon (0.4 was run directly by couchdb).
<li>URL's now require the full design document id (where you would say "foo", you must now say "_design/foo").
</ul>

<h1>Issue Tracking</h1>

Issue tracking at <a href="http://github.com/rnewson/couchdb-lucene/issues">github</a>.

<h1>Minimum System Requirements</h1>

Java 1.5 (or above) is required; the <strike>Sun</strike> Oracle version is recommended as it's regularly tested against.

<h1>Build and run couchdb-lucene</h1>

If you are on OS X, you might find it easiest to;

<pre>
brew install couchdb-lucene
</pre>

<ol>
<li>Install Maven (2 or 3).
<li>checkout repository
<li>type 'mvn'
<li>cd target
<li>unzip couchdb-lucene-&lt;version&gt;.zip
<li>cd couchdb-lucene-&lt;version&gt;
<li>./bin/run
</ol>

The zip file contains all the couchdb-lucene code, dependencies, startup scripts and configuration files you need, so unzip it wherever you wish to install couchdb-lucene.

If you want to run couchdb-lucene on a servlet container like Tomcat, you can build the war file using Maven

<pre>
mvn war:war
</pre>

<h1>Configure CouchDB</h1>

The following settings are needed in CouchDB's local.ini file in order for it to communicate with couchdb-lucene;

<h2>For CouchDB versions prior to 1.1</h2>
<pre>
[couchdb]
os_process_timeout=60000 ; increase the timeout from 5 seconds.

[external]
fti=/path/to/python /path/to/couchdb-lucene/tools/couchdb-external-hook.py

[httpd_db_handlers]
_fti = {couch_httpd_external, handle_external_req, &lt;&lt;"fti"&gt;&gt;}
</pre>

<h2>For CouchDB versions from 1.1 onward</h2>
<pre>
[httpd_global_handlers]
_fti = {couch_httpd_proxy, handle_proxy_req, &lt;&lt;"http://127.0.0.1:5985"&gt;&gt;}
</pre>

<b>Note:</b> The urls via the proxy have a different form:

<pre>http://127.0.0.1:5984/_fti/local/db1/_design/cl-test/idx?q=hello</pre>

The "local" matches the name of the key from <code>couchdb-lucene.ini</code>.

<h2>Hook options</h2>

<table>
<tr><th>Option</th><th>Meaning</th><th>Default Value</th></tr>
<tr><td>--remote-host</td><td>The hostname of the couchdb-lucene server</td><td>localhost</td></tr>
<tr><td>--remote-port</td><td>The port of the couchdb-lucene server</td><td>5985</td></tr>
<tr><td>--local-key</td><td>The key for the local couchdb instance as known to the couchdb-lucene server</td><td>local</td></tr>
</table>

<h1>Configure couchdb-lucene</h1>

couchdb-lucene runs in a single, standalone JVM. As such, you can choose to locate your couchdb-lucene server on a different machine to couchdb if you wish, or keep it on the same machine, it's your call.

<h1>Start couchdb-lucene</h1>

To start couchdb-lucene, run;
<pre>
bin/run
</pre>

To stop couchdb-lucene, simply kill the Java process.

<h1>Indexing Strategy</h1>

<h2>Document Indexing</h2>

You must supply a index function in order to enable couchdb-lucene as, by default, nothing will be indexed. To suppress a document from the index, return null. It's more typical to return a single Document object which contains everything you'd like to query and retrieve. You may also return an array of Document objects if you wish.

You may add any number of index views in any number of design documents. All searches will be constrained to documents emitted by the index functions.

Here's an complete example of a design document with couchdb-lucene features:

<pre>
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
</pre>

Here are some example URL's for the given design document;

<pre>
http://localhost:5984/database/_fti/_design/foo/by_subject?q=hello
http://localhost:5984/database/_fti/_design/foo/by_content?q=hello
</pre>

A fulltext object contains multiple index view declarations. An index view consists of;

<dl>
<dt>analyzer</dt><dd>(optional) The analyzer to use</dd>
<dt>defaults</dt><dd>(optional) The default for numerous indexing options can be overridden here. A full list of options follows.</dd>
<dt>index</dt><dd>The indexing function itself, documented below.</dd>
</dl>

<h3>The Defaults Object</h3>

The following indexing options can be defaulted;

<table>
  <tr>
    <th>name</th>
    <th>description</th>
    <th>available options</th>
    <th>default</th>
  </tr>
  <tr>
    <th>field</th>
    <td>the field name to index under</td>
    <td>user-defined</td>
    <td>default</td>
  </tr>
  <tr>
    <th>type</th>
    <td>the type of the field</td>
    <td>date, double, float, int, long, string</td>
    <td>string</td>
  </tr>
  <tr>
    <th>store</th>
    <td>whether the data is stored. The value will be returned in the search result.</td>
    <td>yes, no</td>
    <td>no</td>
  </tr>
  <tr>
    <th>index</th>
    <td>whether (and how) the data is indexed</td>
    <td>analyzed, analyzed_no_norms, no, not_analyzed, not_analyzed_no_norms</td>
    <td>analyzed</td>
  </tr>
  <tr>
    <th>termvector</th>
    <td>whether and how a field should have term vectors</td>
    <td>no, with_offsets, with_positions, with_positions_offsets, yes</td>
    <td>no</td>
  </tr>
  <tr>
    <th>boost</th>
    <td>Sets the boost factor hits on this field. This value will be multiplied into the score of all hits on this this field of this document.</td>
    <td>floating-point value</td>
    <td>1.0</td>
  </tr>
</table>

<h3>The Analyzer Option</h3>

Lucene has numerous ways of converting free-form text into tokens, these classes are called Analyzer's. By default, the StandardAnalyzer is used which lower-cases all text, drops common English words ("the", "and", and so on), among other things. This processing might not always suit you, so you can choose from several others by setting the "analyzer" field to one of the following values;

<ul>
<li>brazilian</li>
<li>chinese</li>
<li>cjk</li>
<li>czech</li>
<li>dutch</li>
<li>english</li>
<li>french</li>
<li>german</li>
<li>keyword</li>
<li>perfield</li>
<li>porter</li>
<li>russian</li>
<li>simple</li>
<li>snowball</li>
<li>standard</li>
<li>thai</li>
<li>whitespace</li>
</ul>

<h4>The Snowball Analyzer</h4>

This analyzer requires an extra argument to specify the language (see <a href="http://lucene.apache.org/java/3_0_3/api/contrib-snowball/org/apache/lucene/analysis/snowball/SnowballAnalyzer.html">here</a> for details);

<pre>
"analyzer":"snowball:English"
</pre>

Note: the argument is case-sensitive and is passed directly to the <code>SnowballAnalyzer</code>'s constructor.

<h4>The Per-field Analyzer"</h4>

The "perfield" option lets you use a different analyzer for different fields and is configured as follows;

<pre>
"analyzer":"perfield:{field_name:\"analyzer_name\"}"
</pre>

Unless overridden, any field name not specified will be handled by the standard analyzer. To change the default, use the special default field name;

<pre>
"analyzer":"perfield:{default:\"keyword\"}"
</pre>

<h3>The Document class</h3>

You may construct a new Document instance with;

<pre>
var doc = new Document();
</pre>

Data may be added to this document with the add method which takes an optional second object argument that can override any of the above default values.

<pre>
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
</pre>

<h3>Example Index Functions</h3>

<h4>Index Everything</h4>

<pre>
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
</pre>

<h4>Index Nothing</h4>

<pre>
function(doc) {
  return null;
}
</pre>

<h4>Index Select Fields</h4>

<pre>
function(doc) {
  var result = new Document();
  result.add(doc.subject, {"field":"subject", "store":"yes"});
  result.add(doc.content, {"field":"subject"});
  result.add(new Date(), {"field":"indexed_at"});
  return result;
}
</pre>

<h4>Index Attachments</h4>

<pre>
function(doc) {
  var result = new Document();
  for(var a in doc._attachments) {
    result.attachment("default", a);
  }
  return result;
}
</pre>

<h4>A More Complex Example</h4>

<pre>
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
</pre>

<h2>Attachment Indexing</h2>

Couchdb-lucene uses <a href="http://lucene.apache.org/tika/">Apache Tika</a> to index attachments of the following types, assuming the correct content_type is set in couchdb;

<h3>Supported Formats</h3>

<ul>
<li>Excel spreadsheets (application/vnd.ms-excel)
<li>HTML (text/html)
<li>Images (image/*)
<li>Java class files
<li>Java jar archives
<li>MP3 (audio/mp3)
<li>OpenDocument (application/vnd.oasis.opendocument.*)
<li>Outlook (application/vnd.ms-outlook)
<li>PDF (application/pdf)
<li>Plain text (text/plain)
<li>Powerpoint presentations (application/vnd.ms-powerpoint)
<li>RTF (application/rtf)
<li>Visio (application/vnd.visio)
<li>Word documents (application/msword)
<li>XML (application/xml)
</ul>

<h1>Searching with couchdb-lucene</h1>

You can perform all types of queries using Lucene's default <a href="http://lucene.apache.org/java/3_5_0/queryparsersyntax.html">query syntax</a>.

<h2>Numeric range queries</h2>

In addition to normal text-based range searches (using the "field:[lower TO upper]" syntax), couchdb-lucene also supports numeric range searches for the following types: int, long, float, double and date. The type is specified after the field name, as follows;

<table>
<tr><td>type</td><td>example</td></tr>
<tr><td>int</td><td>field&lt;int>:[0 TO 100]</td></tr>
<tr><td>long</td><td>field&lt;long>:[0 TO 100]</td></tr>
<tr><td>float</td><td>field&lt;float>:[0.0 TO 100.0]</td></tr>
<tr><td>double</td><td>field&lt;double>:[0.0 TO 100.0]</td></tr>
<tr><td>date</td><td>field&lt;date>:[from TO to] where from and to match any of these patterns: <code>"yyyy-MM-dd'T'HH:mm:ssZ"</code>, <code>"yyyy-MM-dd'T'HH:mm:ss"<code>, <code>"yyyy-MM-ddZ"M/code>, <code>"yyyy-MM-dd"</code>, <code>"yyyy-MM-dd'T'HH:mm:ss.SSSZ"</code>, <code>"yyyy-MM-dd'T'HH:mm:ss.SSS"</code>. So, in order to search for articles published in July, you would issue a following query: <code>published_at&lt;date&gt;:["2010-07-01T00:00:00"+TO+"2010-07-31T23:59:59"]</code></td></tr>
</table>

An example numeric range query for spatial searching.

<pre>
?q=pizza AND lat&lt;double>:[51.4707 TO 51.5224] AND long&lt;double>:[-0.6622 TO -0.5775]
</pre>

<h2>Numeric term queries</h2>

Fields indexed with numeric types can still be queried as normal terms, couchdb-lucene just needs to know the type. For example, ?q=age&lt;long&gt;:12 will find all documents where the field called 'age' has a value of 12 (when the field was indexed as "type":"int".

<h2>Search parameters</h2>

The following parameters can be passed for more sophisticated searches;

<dl>
<dt>analyzer</dt><dd>Override the default analyzer used to parse the q parameter</dd>
<dt>callback</dt><dd>Specify a JSONP callback wrapper. The full JSON result will be prepended with this parameter and also placed with parentheses."</dd>
<dt>debug</dt><dd>Setting this to true disables response caching (the query is executed every time) and indents the JSON response for readability.</dd>
<dt>default_operator</dt><dd>Change the default operator for boolean queries. Defaults to "OR", other permitted value is "AND".</dd>
<dt>force_json<dt><dd>Usually couchdb-lucene determines the Content-Type of its response based on the presence of the Accept header. If Accept contains "application/json", you get "application/json" in the response, otherwise you get "text/plain;charset=utf8". Some tools, like JSONView for FireFox, do not send the Accept header but do render "application/json" responses if received. Setting force_json=true forces all response to "application/json" regardless of the Accept header.</dd>
<dt>include_docs</dt><dd>whether to include the source docs</dd>
<dt>include_fields</dt><dd>By default, <i>all</i> stored fields are returned with results. Use a comma-separate list of field names with this parameter to refine the response</dd>
<dt>limit</dt><dd>the maximum number of results to return</dd>
<dt>q</dt><dd>the query to run (e.g, subject:hello). If not specified, the default field is searched. Multiple queries can be supplied, separated by commas; the resulting JSON will be an array of responses.</dd>
<dt>skip</dt><dd>the number of results to skip</dd>
<dt>sort</dt><dd>the comma-separated fields to sort on. Prefix with / for ascending order and \ for descending order (ascending is the default if not specified). Type-specific sorting is also available by appending the type between angle brackets (e.g, sort=amount&lt;float&gt;). Supported types are 'float', 'double', 'int', 'long' and 'date'.</dd>
<dt>stale=ok</dt><dd>If you set the <i>stale</i> option to <i>ok</i>, couchdb-lucene will not block if the index is not up to date and it will immediately return results. Therefore searches may be faster as Lucene caches important data (especially for sorting). A query without stale=ok will block and use the latest data committed to the index. Unlike CouchDBs stale=ok option for views, couchdb-lucene will trigger an index update unless one is already running.</dd>
</dl>

<i>All parameters except 'q' are optional.</i>

<h2>Special Fields</h2>

<dl>
<dt>_id</dt><dd>The _id of the document.</dd>
</dl>

<h2>Dublin Core</h2>

All Dublin Core attributes are indexed and stored if detected in the attachment. Descriptions of the fields come from the Tika javadocs.

<dl>
<dt>_dc.contributor</dt><dd> An entity responsible for making contributions to the content of the resource.</dd>
<dt>_dc.coverage</dt><dd>The extent or scope of the content of the resource.</dd>
<dt>_dc.creator</dt><dd>An entity primarily responsible for making the content of the resource.</dd>
<dt>_dc.date</dt><dd>A date associated with an event in the life cycle of the resource.</dd>
<dt>_dc.description</dt><dd>An account of the content of the resource.</dd>
<dt>_dc.format</dt><dd>Typically, Format may include the media-type or dimensions of the resource.</dd>
<dt>_dc.identifier</dt><dd>Recommended best practice is to identify the resource by means of a string or number conforming to a formal identification system.</dd>
<dt>_dc.language</dt><dd>A language of the intellectual content of the resource.</dd>
<dt>_dc.modified</dt><dd>Date on which the resource was changed.</dd>
<dt>_dc.publisher</dt><dd>An entity responsible for making the resource available.</dd>
<dt>_dc.relation</dt><dd>A reference to a related resource.</dd>
<dt>_dc.rights</dt><dd>Information about rights held in and over the resource.</dd>
<dt>_dc.source</dt><dd>A reference to a resource from which the present resource is derived.</dd>
<dt>_dc.subject</dt><dd>The topic of the content of the resource.</dd>
<dt>_dc.title</dt><dd>A name given to the resource.</dd>
<dt>_dc.type</dt><dd>The nature or genre of the content of the resource.</dd>
</dl>

<h2>Examples</h2>

<pre>
http://localhost:5984/dbname/_fti/_design/foo/view_name?q=field_name:value
http://localhost:5984/dbname/_fti/_design/foo/view_name?q=field_name:value&sort=other_field
http://localhost:5984/dbname/_fti/_design/foo/view_name?debug=true&sort=billing_size&lt;long&gt;&q=body:document AND customer:[A TO C]
</pre>

<h2>Search Results Format</h2>

The search result contains a number of fields at the top level, in addition to your search results.

<dl>
<dt>etag</dt><dd>An opaque token that reflects the current version of the index. This value is also returned in an ETag header to facilitate HTTP caching.</dd>
<dt>fetch_duration</dt><dd>The number of milliseconds spent retrieving the documents.</dd>
<dt>limit</dt><dd>The maximum number of results that can appear.</dd>
<dt>q</dt><dd>The query that was executed.</dd>
<dt>rows</dt><dd>The search results array, described below.</dd>
<dt>search_duration</dt><dd>The number of milliseconds spent performing the search.</dd>
<dt>skip</dt><dd>The number of initial matches that was skipped.</dd>
<dt>total_rows</dt><dd>The total number of matches for this query.</dd>
</dl>

<h2>The search results array</h2>

The search results arrays consists of zero, one or more objects with the following fields;

<dl>
<dt>doc</dt><dd>The original document from couch, if requested with include_docs=true</dd>
<dt>fields</dt><dd>All the fields that were stored with this match</dd>
<dt>id</dt><dd>The unique identifier for this match.</dd>
<dt>score</dt><dd>The normalized score (0.0-1.0, inclusive) for this match</dd>
</dl>

Here's an example of a JSON response without sorting;

<pre>
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
</pre>

And the same with sorting;

<pre>
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
</pre>

<h3>Content-Type of response</h3>

The Content-Type of the response is negotiated via the Accept request header like CouchDB itself. If the Accept header includes "application/json" then that is also the Content-Type of the response. If not, "text/plain;charset=utf-8" is used.

<h1>Fetching information about the index</h1>

Calling couchdb-lucene without arguments returns a JSON object with information about the index.

<pre>
http://127.0.0.1:5984/&lt;db>/_fti/_design/foo/&lt;index&gt;
</pre>

returns;

<pre>
{"current":true,"disk_size":110674,"doc_count":397,"doc_del_count":0,
"fields":["default","number"],"last_modified":"1263066382000",
"optimized":true,"ref_count":2}
</pre>

<h1>Index Maintenance</h1>

For optimal query speed you can optimize your indexes. This causes the index to be rewritten into a single segment.

<pre>
curl -X POST http://localhost:5984/&lt;db>/_fti/_design/foo/&lt;index>/_optimize
</pre>

If you just want to expunge pending deletes, then call;

<pre>
curl -X POST http://localhost:5984/&lt;db>/_fti/_design/foo/&lt;index>/_expunge
</pre>

If you recreate databases or frequently change your fulltext functions, you will probably have old indexes lying around on disk. To remove all of them, call;

<pre>
curl -X POST http://localhost:5984/&lt;db>/_fti/_cleanup
</pre>

<h1>Authentication</h1>

By default couchdb-lucene does not attempt to authenticate to CouchDB. If you have set CouchDB's require_valid_user to true, you will need to modify couchdb-lucene.ini. Change the url setting to include a valid username and password. e.g, the default setting is;

<pre>
[local]
url=http://localhost:5984/
</pre>

Change it to;

<pre>
[local]
url=http://foo:bar@localhost:5984/
</pre>

and couchdb-lucene will authenticate to couchdb.

<h1>Other Tricks</h1>

A couple of 'expert' options can be set in the couchdb-lucene.ini file;

Leading wildcards are prohibited by default as they perform very poorly most of the time. You can enable them as follows;

<pre>
[lucene]
allowLeadingWildcard=true
</pre>

Lucene automatically converts terms to lower case in wildcard situations. You can disable this with;

<pre>
[lucene]
lowercaseExpandedTerms=false
</pre>
