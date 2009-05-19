<h1>NOTES</h1>

This documentation is slightly ahead of the code; the "language" and "analyzer" options are not yet available.

<h1>News</h1>

The indexing API in 0.3 has changed since 0.2 to  allow multiple design documents and "views" into Lucene. It will moves the Lucene-specific stuff into an options object.

<h1>Issue Tracking</h1>

Issue tracking at <a href="http://github.com/rnewson/couchdb-lucene/issues">github</a>.

<h1>System Requirements</h1>

Sun JDK 5 or higher is recommended. 

Couchdb-lucene is known to be incompatible with some versions of OpenJDK as it includes an earlier, and incompatible, version of the Rhino Javascript library. The version in Ubuntu 8.10 (6b12-0ubuntu6.4) is known to work and it uses Rhino 1.7R1.

<h1>Build couchdb-lucene</h1>

<ol>
<li>Install Maven 2.
<li>checkout repository
<li>type 'mvn'
<li>configure couchdb (see below)
</ol>

<h1>Configure CouchDB</h1>

<pre>
[couchdb]
os_process_timeout=60000 ; increase the timeout from 5 seconds.

[external]
fti=/usr/bin/java -jar /path/to/couchdb-lucene*-jar-with-dependencies.jar -search

[update_notification]
indexer=/usr/bin/java -jar /path/to/couchdb-lucene*-jar-with-dependencies.jar -index

[httpd_db_handlers]
_fti = {couch_httpd_external, handle_external_req, <<"fti">>}
</pre>

<h1>Indexing Strategy</h1>

<h2>Document Indexing</h2>

You must supply a index function in order to enable couchdb-lucene as by default, nothing will be indexed.

You may add any number of index views in any number of design documents. All searches will be constrained to documents emitted by the index functions.

Declare your functions as follows;

<pre>
{
  "fulltext": {
    "by_subject": {
      "defaults": { "store":"yes" },
      "index":"function(doc) { var ret=new Document(); ret.add(doc.subject); return ret }"
    },
    "french_documents": {
      "defaults": { "language":"fr" },
      "index":"function(doc) { if (doc.language != "fr") { return null;} var ret=new Document(); <i>etc</i> return ret;  }"
    }
  }
}
</pre>

A fulltext object contains multiple index view declarations. An index view consists of;

<dl>
<dt>defaults</dt><dd>The default for numerous indexing options can be overridden here. A full list of options follows.</dd>
<dt>index</dt><dd>The indexing function itself, documented below.</dd>

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
    <th>analyzer</th>
    <td>how the data is analyzed</td>
    <td>auto, simple, standard</td>
    <td>auto</td>
  </tr>	
  <tr>
    <th>language</th>
    <td>which language the data is in</td>
    <td>auto, br, cjk, cn, cz, de, el, en, fr, nl, ru, th</td>
    <td>en</td>
  </tr>	
</table>

<h3>The Document class</h3>

You may construct a new Document instance with;

<pre>
var doc = new Document();
</pre>

Data may be added to this document with the add method which takes an optional second object argument that can override any of the above default values.

The data is usually interpreted as a String but couchdb-lucene provides special handling if a Javascript Date object is passed. Specifically, the date is indexed as a numeric value, which allows correct sorting, and stored (if requested) in ISO 8601 format (with a timezone marker).

<pre>
// Add with all the defaults.
doc.add("value");

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
  result.add({"field":"indexed_at"});
  return result;
}
</pre>

<h4>Index Attachments</h4>

<pre>
function(doc) {
  var result = new Document();
  for(var a in doc._attachments) {
    result.add_attachment(a, {"field":"attachment"});
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
    var ret = [];
    if(doc.type != "reference") return null;
    for(var g in doc.groups) {
        ret.add(mk("library", doc.groups[g].library, g));
        ret.add(mk("method", doc.groups[g].method, g));
        ret.add(mk("target", doc.groups[g].target, g));
    }
    return ret;
}
</pre>

<h2>Attachment Indexing</h2>

Couchdb-lucene uses <a href="http://lucene.apache.org/tika/">Apache Tika</a> to index attachments of the following types, assuming the correct content_type is set in couchdb;

<h3>Supported Formats</h3>

<ul>
<li>Excel spreadsheets (application/vnd.ms-excel)
<li>Word documents (application/msword)
<li>Powerpoint presentations (application/vnd.ms-powerpoint)
<li>Visio (application/vnd.visio)
<li>Outlook (application/vnd.ms-outlook)
<li>XML (application/xml)
<li>HTML (text/html)
<li>Images (image/*)
<li>Java class files
<li>Java jar archives
<li>MP3 (audio/mp3)
<li>OpenDocument (application/vnd.oasis.opendocument.*)
<li>Plain text (text/plain)
<li>PDF (application/pdf)
<li>RTF (application/rtf)
</ul>

<h1>Searching with couchdb-lucene</h1>

You can perform all types of queries using Lucene's default <a href="http://lucene.apache.org/java/2_4_0/queryparsersyntax.html">query syntax</a>. The _body field is searched by default which will include the extracted text from all attachments. The following parameters can be passed for more sophisticated searches;

<dl>
<dt>q</dt><dd>the query to run (e.g, subject:hello). If not specified, the default field is searched.</dd>
<dt>lang</dt><dd>The language that the query parameter is in. Available options, and the default if not specified, are identical to the language option specified above.</dd>
<dt>sort</dt><dd>the comma-separated fields to sort on. Prefix with / for ascending order and \ for descending order (ascending is the default if not specified).</dd>
<dt>limit</dt><dd>the maximum number of results to return</dd>
<dt>skip</dt><dd>the number of results to skip</dd>
<dt>include_docs</dt><dd>whether to include the source docs</dd>
<dt>stale=ok</dt><dd>If you set the <i>stale</i> option <i>ok</i>, couchdb-lucene may not perform any refreshing on the index. Searches may be faster as Lucene caches important data (especially for sorting). A query without stale=ok will use the latest data committed to the index.</dd>
<dt>debug</dt><dd>if false, a normal application/json response with results appears. if true, an pretty-printed HTML blob is returned instead.</dd>
<dt>rewrite</dt><dd>(EXPERT) if true, returns a json response with a rewritten query and term frequencies. This allows correct distributed scoring when combining the results from multiple nodes.</dd>
</dl>

<i>All parameters except 'q' are optional.</i>

<h2>Special Fields</h2>

<dl>
<dt>_db</dt><dd>The source database of the document.</dd>
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
<dt>q</dt><dd>The query that was executed.</dd>
<dt>etag</dt><dd>An opaque token that reflects the current version of the index. This value is also returned in an ETag header to facilitate HTTP caching.</dd>
<dt>skip</dt><dd>The number of initial matches that was skipped.</dd>
<dt>limit</dt><dd>The maximum number of results that can appear.</dd>
<dt>total_rows</dt><dd>The total number of matches for this query.</dd>
<dt>search_duration</dt><dd>The number of milliseconds spent performing the search.</dd>
<dt>fetch_duration</dt><dd>The number of milliseconds spent retrieving the documents.</dd>
<dt>rows</dt><dd>The search results array, described below.</dd>
</dl>

<h2>The search results array</h2>

The search results arrays consists of zero, one or more objects with the following fields;

<dl>
<dt>id</dt><dd>The unique identifier for this match.</dd>
<dt>score</dt><dd>The normalized score (0.0-1.0, inclusive) for this match</dd>
<dt>fields</dt><dd>All the fields that were stored with this match</dd>
<dt>doc</dt><dd>The original document from couch, if requested with include_docs=true</dd>
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

<h1>Fetching information about the index</h1>

Calling couchdb-lucene without arguments returns a JSON object with information about the <i>whole</i> index.

<pre>
http://127.0.0.1:5984/enron/_fti
</pre>

returns;

<pre>
{"doc_count":517350,"doc_del_count":1,"disk_size":318543045}
</pre>

<h1>Working With The Source</h1>

To develop "live", type "mvn dependency:unpack-dependencies" and change the external line to something like this;

<pre>
fti=/usr/bin/java -cp /path/to/couchdb-lucene/target/classes:\
/path/to/couchdb-lucene/target/dependency com.github.rnewson.couchdb.lucene.Main
</pre>

You will need to restart CouchDB if you change couchdb-lucene source code but this is very fast.

<h1>Configuration</h1>

couchdb-lucene respects several system properties;

<dl>
<dt>couchdb.url</dt><dd>the url to contact CouchDB with (default is "http://localhost:5984")</dd>
<dt>couchdb.lucene.dir</dt><dd>specify the path to the lucene indexes (the default is to make a directory called 'lucene' relative to couchdb's current working directory.</dd>
<dt>couchdb.log.dir</dt><dd>specify the directory of the log file (which is called couchdb-lucene.log), defaults to the platform-specific temp directory.</dd>
</dl>

You can override these properties like this;

<pre>
fti=/usr/bin/java -Dcouchdb.lucene.dir=/tmp \
-cp /home/rnewson/Source/couchdb-lucene/target/classes:\
/home/rnewson/Source/couchdb-lucene/target/dependency\
com.github.rnewson.couchdb.lucene.Main
</pre>

<h2>Basic Authentication</h2>

If you put couchdb behind an authenticating proxy you can still configure couchdb-lucene to pull from it by specifying additional system properties. Currently only Basic authentication is supported.

<dl>
<dt>couchdb.user</dt><dd>the user to authenticate as.</dd>
<dt>couchdb.password</dt><dd>the password to authenticate with.</dd>
</dl>

<h2>IPv6</h2>

The default for couchdb.url is problematic on an IPv6 system. Specify -Dcouchdb.url=http://[::1]:5984 to resolve it.
