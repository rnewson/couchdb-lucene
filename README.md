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

Currently all fields of all documents are indexed, javascript control coming soon.

<h1>Searching with couchdb-lucene</h1>

You can perform all types of queries using Lucene's default <a href="http://lucene.apache.org/java/2_4_0/queryparsersyntax.html">query syntax</a>. The following parameters can be passed for more sophisticated searches;

<dl>
<dt>q<dd>the query to run (e.g, subject:hello)<
<dt>sort<dd>the comma-separated fields to sort on.
<dt>asc<dd>sort ascending (true) or descending (false), only when sorting on a single field.
<dt>limit<dd>the maximum number of results to return
<dt>skip<dd>the number of results to skip
<dt>include_docs<dd>whether to include the source docs
<dt>debug<dd>if false, a normal application/json response with results appears. if true, an pretty-printed HTML blob is returned instead.

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

<h1>Working With The Source</h1>

To develop "live", type "mvn dependency:unpack-dependencies" and change the external line to something like this;

<pre>
fti=/usr/bin/java -cp /home/rnewson/Source/couchdb-lucene/target/classes:/home/rnewson/Source/couchdb-lucene/target/dependency org.apache.couchdb.lucene.Main
</pre>

You will need to restart CouchDB if you change couchdb-lucene source code but this is very fast.

<h1>Configuration</h1>

couchdb-lucene respects several system properties;

<dl>
<dd>couchdb.url<dt>the url to contact CouchDB with (default is "http://localhost:5984")
<dd>couchdb.lucene.dir<dt>specify the path to the lucene indexes (the default is to make a directory called 'lucene' relative to couchdb's current working directory.
</dl>

You can override these properties like this;

<pre>
fti=/usr/bin/java -D couchdb.lucene.dir=/tmp -cp /home/rnewson/Source/couchdb-lucene/target/classes:/home/rnewson/Source/couchdb-lucene/target/dependency org.apache.couchdb.lucene.Main
</pre>
