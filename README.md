<h1>Build couchdb-lucene</h1>

<ol>
<li>Install Maven 2.
<li>checkout repository
<li>type 'mvn'
<li>configure couchdb (see below)
</ol>

<h1>Configure CouchDB</h1>

<pre>
[external]
fti= /usr/bin/java -jar /path/to/couchdb-lucene*-jar-with-dependencies.jar

[httpd_db_handlers]
_fti = {couch_httpd_external, handle_external_req, <<"fti">>}
</pre>

<h1>Indexing Strategy</h1>

<h2>Document Indexing</h2>

Currently all fields of all documents are indexed, javascript control coming soon.

<h2>Attachment Indexing</h2>

CouchDB uses <a href="http://lucene.apache.org/tika/">Apache Tika</a> to index attachments of the following types, assuming the correct content_type is set in couchdb;

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

You can perform all types of queries using Lucene's default <a href="http://lucene.apache.org/java/2_4_0/queryparsersyntax.html">query syntax</a>. The following parameters can be passed for more sophisticated searches;

<dl>
<dt>q<dd>the query to run (e.g, subject:hello)
<dt>sort<dd>the comma-separated fields to sort on.
<dt>asc<dd>sort ascending (true) or descending (false), only when sorting on a single field.
<dt>limit<dd>the maximum number of results to return
<dt>skip<dd>the number of results to skip
<dt>include_docs<dd>whether to include the source docs
<dt>debug<dd>if false, a normal application/json response with results appears. if true, an pretty-printed HTML blob is returned instead.
</dl>

<i>All parameters except 'q' are optional.</i>

<h2>Examples</h2>

<pre>
http://localhost:5984/dbname/_fti?q=field_name:value
http://localhost:5984/dbname/_fti?q=field_name:value&sort=other_field
http://localhost:5984/dbname/_fti?debug=true&sort=billing_size&q=body:document AND customer:[A TO C]
http://localhost:5984/dbname/_fti?debug=true&sort=billing_size&q=body:document AND customer:[100 TO 400]
</pre>

<h2>Search Results Format</h2>

return values is a JSON array of _id, _rev and sort_field values (the latter only when sort= is supplied)

<pre>
{
  "total_rows":49999,
  "rows":
  [
    {"_id":"9","_rev":"2779848574","score":1.712123155593872},
    {"_id":"8","_rev":"670155834","score":1.712123155593872}
  ]
}
</pre>

<pre>
{
  "total_rows":49999,
  "sort_order":
  [
    {"field":"customer","reverse":false,"type":"string"},
    {"reverse":false,"type":"doc"}
  ],
  "rows":
  [
    {"_id":"75000","_rev":"372496647","score":1.712123155593872,"sort_order":["00000000000000",50802]},
    {"_id":"170036","_rev":"3628205594","score":1.712123155593872,"sort_order":["00000000000000",51716]}
  ]
}
</pre>

<h1>Working With The Source</h1>

To develop "live", type "mvn dependency:unpack-dependencies" and change the external line to something like this;

<pre>
fti=/usr/bin/java -cp /path/to/couchdb-lucene/target/classes:\
/path/to/couchdb-lucene/target/dependency org.apache.couchdb.lucene.Main
</pre>

You will need to restart CouchDB if you change couchdb-lucene source code but this is very fast.

<h1>Configuration</h1>

couchdb-lucene respects several system properties;

<dl>
<dt>couchdb.url<dd>the url to contact CouchDB with (default is "http://localhost:5984")
<dt>couchdb.lucene.dir<dd>specify the path to the lucene indexes (the default is to make a directory called 'lucene' relative to couchdb's current working directory.
</dl>

You can override these properties like this;

<pre>
fti=/usr/bin/java -D couchdb.lucene.dir=/tmp \
-cp /home/rnewson/Source/couchdb-lucene/target/classes:\
/home/rnewson/Source/couchdb-lucene/target/dependency\
org.apache.couchdb.lucene.Main
</pre>
