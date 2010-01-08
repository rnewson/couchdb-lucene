<h1>WARNING</h1>

0.5 is not yet released and may contain bugs and regressions. However, it is shaping up nicely so I recommend you try it first. 0.4 remains available if you hit insurmountable problems; but please take time to file a ticket at github if you do.

<h1>Version Compatibility</h1>

<table>
<tr><th>CouchDB</th><th>couchdb-lucene</th></tr>
<tr><td>0.9.1, 0.10</td><td>0.4</td></tr>
<tr><td>0.11 (not yet released)</td><td>0.4-maint (0.4 with patch for trunk compatibility)</td></tr>
<tr><td>0.10+</td><td>0.5 (not yet released)</td></tr>
</table>

<h1>Issue Tracking</h1>

Issue tracking at <a href="http://github.com/rnewson/couchdb-lucene/issues">github</a>.

<h1>Minimum System Requirements</h1>

Java 1.5 (or above) is required; the Sun version is recommended as it's regularly tested against.

<h1>Build couchdb-lucene</h1>

<ol>
<li>Install Maven 2.
<li>checkout repository
<li>type 'mvn'
<li>configure couchdb (see below)
</ol>

You will now have a zip file in the target/ directory. This contains all the couchdb-lucene code, dependencies, startup scripts and configuration files to run couchdb-lucene.

<h1>Configure CouchDB</h1>

<pre>
[couchdb]
os_process_timeout=60000 ; increase the timeout from 5 seconds.

[external]
fti=/path/to/python /path/to/couchdb-lucene/tools/couchdb-external-hook.py

[httpd_db_handlers]
_fti = {couch_httpd_external, handle_external_req, <<"fti">>}
</pre>

<h2>Hook options</h2>

<table>
<tr><th>Option</th><th>Meaning</th><th>Default Value</th></tr>
<tr><td>--remote-host</td><td>The hostname of the couchdb-lucene server</td><td>localhost</td></tr>
<tr><td>--remote-port</td><td>The port of the couchdb-lucene server</td><td>5985</td></tr>
<tr><td>--local-host</td><td>The hostname of the couchdb server</td><td>localhost</td></tr>
<tr><td>--local-port</td><td>The port of the couchdb server</td><td>5984</td></tr>
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
    "_id":"_design/a_design_document_with_any_name_you_like",
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
http://localhost:5984/database/_fti/lucene/by_subject?q=hello
http://localhost:5984/database/_fti/lucene/by_content?q=hello
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
    <td>date, double, float, integer, long, string</td>
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
<li>standard</li>
<li>thai</li>
</ul>

Note: You must also supply analyzer=<analyzer_name> as a query parameter to ensure that queries are processed correctly.

The "perfield" option lets you use a different analyzer for different fields and is configured as follows;

<pre>
?analyzer=perfield:{field_name:"analyzer_name"}
</pre>

Unless overridden, any field name not specified will be handled by the standard analyzer. To change the default, use the special default field name;

<pre>
?analyzer=perfield:{default:"keyword"}
</pre>

You should also specify the analyzer in your ddoc too;

<pre>
"fulltext": {
  "idx": {
    "analyzer": "perfield:{default:\"keyword\"}"
  }
}
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
doc.add(35, {"type":"integer"});

// Add a date field.
doc.add(new Date("2009-01-01"), {"type":"date"});

// Add a date field (object must be a Date object

// Add a subject field.
doc.add("this is the subject line.", {"field":"subject"});

// Add but ensure it's stored.
doc.add("value", {"store":"yes"});

// Add but don't analyze.
doc.add("don't analyze me", {"index":"not_analyzed"});

// Extract text from the named attachment and index it (but not store it).
doc.attachment("attachment name", {"field":"attachments"});
</pre>

<h3>Example Transforms</h3>

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
	    ret.attachment("attachment", i);
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
    result.attachment(a, {"field":"attachment"});
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

You can perform all types of queries using Lucene's default <a href="http://lucene.apache.org/java/2_4_0/queryparsersyntax.html">query syntax</a>.

<h2>Numeric range queries</h2>

In addition to normal text-based range searches (using the "field:[lower TO upper]" syntax), couchdb-lucene all supports numeric range searches for the following types: integer, long, float, double and date. The type is automatically derived from the search terms used, as follows;

<table>
<tr><td>type</td><td>format</td><td>example</td></tr>
<tr><td>integer</td><td>[0-9]+</td><td>[0 TO 100]</td></tr>
<tr><td>long</td><td>[0-9]+L</td><td>[0L TO 100L]</td></tr>
<tr><td>float</td><td>[0-9]+.[0-9]+f</td><td>[0.0f TO 100.0f]</td></tr>
<tr><td>double</td><td>[0-9]+.[0-9]+</td><td>[0.0 TO 100.0]</td></tr>
<tr><td>date</td><td>yyyy-MM-dd or yyyy-MM-ddZZ or yyyy-MM-dd'T'HH:mm:ss or yyyy-MM-dd'T'HH:mm:ssZZ</td><td>2001-01-01 or 2001-01-01-0500 or 2000-01-01T00:00:00 or 2000-01-01T00:00:00-0500</td></tr>
</table>

Both the upper and lower bound must be of the same type to trigger numeric range searching. If they don't match, then a normal text-based range search is performed.

The following parameters can be passed for more sophisticated searches;

<dl>
<dt>analyzer</dt><dd>The analyzer used to convert the query string into a query object.
<dt>callback</dt><dd>Specify a JSONP callback wrapper. The full JSON result will be prepended with this parameter and also placed with parentheses."
<dt>debug</dt><dd>Setting this to true disables response caching (the query is executed every time) and indents the JSON response for readability.</dd>
<dt>force_json<dt><dd>Usually couchdb-lucene determines the Content-Type of its response based on the presence of the Accept header. If Accept contains "application/json", you get "application/json" in the response, otherwise you get "text/plain;charset=utf8". Some tools, like JSONView for FireFox, do not send the Accept header but do render "application/json" responses if received. Setting force_json=true forces all response to "application/json" regardless of the Accept header.</dd>
<dt>include_docs</dt><dd>whether to include the source docs</dd>
<dt>limit</dt><dd>the maximum number of results to return</dd>
<dt>q</dt><dd>the query to run (e.g, subject:hello). If not specified, the default field is searched. Multiple q parameters are permitted, the resulting JSON will be an array of responses.</dd>
<dt>skip</dt><dd>the number of results to skip</dd>
<dt>sort</dt><dd>the comma-separated fields to sort on. Prefix with / for ascending order and \ for descending order (ascending is the default if not specified). Type-specific sorting is also available by appending a : and the sort type as normal (e.g, 'sort=amount:float'). Supported types are 'float', 'double', 'int', 'long' and 'date'.</dd>
<dt>stale=ok</dt><dd>If you set the <i>stale</i> option to <i>ok</i>, couchdb-lucene may not perform any refreshing on the index. Searches may be faster as Lucene caches important data (especially for sorting). A query without stale=ok will use the latest data committed to the index.</dd>
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
http://localhost:5984/dbname/_fti/design_doc/view_name?q=field_name:value
http://localhost:5984/dbname/_fti/design_doc/view_name?q=field_name:value&sort=other_field
http://localhost:5984/dbname/_fti/design_doc/view_name?debug=true&sort=billing_size&q=body:document AND customer:[A TO C]
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

Calling couchdb-lucene without arguments returns a JSON object with information about the <i>whole</i> index.

<pre>
http://127.0.0.1:5984/enron/_fti
</pre>

returns;

<pre>
{"doc_count":517350,"doc_del_count":1,"disk_size":318543045}
</pre>
