package com.github.rnewson.cl;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.xml.sax.SAXException;

import com.sun.jersey.multipart.BodyPart;
import com.sun.jersey.multipart.BodyPartEntity;
import com.sun.jersey.multipart.MultiPart;

@Path("/{db}")
@Produces({ "text/plain", "application/json" })
@Consumes({ "text/plain", "application/json" })
public final class FoopyDatabaseResource {

    private static final String FAKE_INSTANCE_START_TIME = "1307900650417";
    private static final String FAKE_REV = "1-967a00dff5e02add41819138abb3284d";
    private static final Version _VERSION = Version.LUCENE_32;
    private static final Analyzer ANALYZER = new StandardAnalyzer(_VERSION);
    private static final Map<String, Directory> DATABASES = new HashMap<String, Directory>();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String db;

    private @Context
    ServletContext servletContext;

    public FoopyDatabaseResource(@PathParam("db") final String db) {
        this.db = db;
    }

    @POST
    @Path("/_bulk_docs2")
    public void bulkDocs(final IndexWriter writer, final List<Document> documents) throws IOException {
        for (final Document document : documents) {
            final Term id = new Term("_id", document.get("_id"));
            writer.updateDocument(id, document);
        }
    }

    @POST
    @Path("/_bulk_docs")
    public Response bulkDocs(final JsonNode bulkDocsRequest) throws IOException {
        System.err.println("context " + servletContext);
        final JsonNode docs = bulkDocsRequest.path("docs");

        final Directory dir = getDirectory(db);
        final IndexWriter writer = writer(dir);
        final Iterator<JsonNode> it = docs.iterator();
        final ArrayNode bulkDocsResponse = MAPPER.createArrayNode();
        while (it.hasNext()) {
            final JsonNode doc = it.next();
            final Document luceneDoc = new Document();
            final Field id = new Field("_id", doc.path("_id").getTextValue(), Store.YES, Index.NOT_ANALYZED);
            luceneDoc.add(id);

            final ObjectNode doc1 = MAPPER.createObjectNode();
            doc1.put("id", doc.path("_id"));
            doc1.put("rev", FAKE_REV);
            bulkDocsResponse.add(doc1);
            writer.addDocument(luceneDoc);
        }
        writer.close();

        return Response.ok().entity(bulkDocsResponse).build();
    }

    @PUT
    public Response create() throws IOException {
        if (DATABASES.containsKey(db)) {
            throw new WebApplicationException(412);
        }
        final Directory dir = new RAMDirectory();
        writer(dir).close();
        DATABASES.put(db, dir);
        return Response.status(201).build();
    }

    @DELETE
    public Response delete() throws IOException {
        final Directory dir = DATABASES.remove(db);
        if (dir == null) {
            throw new WebApplicationException(404);
        }
        for (final String name : dir.listAll()) {
            dir.deleteFile(name);
        }
        return Response.status(200).build();
    }

    @POST
    @Path("/_ensure_full_commit")
    public Response ensureFullCommit() {
        final ObjectNode node = MAPPER.createObjectNode();
        node.put("ok", true);
        node.put("instance_start_time", FAKE_INSTANCE_START_TIME);
        return Response.status(201).entity(node).build();
    }

    @GET
    @Path("/{id}")
    public Response getDocument(@PathParam("id") final String id) {
        final ObjectNode node = MAPPER.createObjectNode();
        node.put("error", "not_found");
        node.put("reason", "missing");
        return Response.status(404).entity(node).build();
    }

    // for CouchDB 1.1
    @GET
    @Path("/_local/{id}")
    public Response getLocalDocument(@PathParam("id") final String id) {
        return getDocument(id);
    }

    @GET
    public ObjectNode info() {
        final ObjectNode node = MAPPER.createObjectNode();
        node.put("db_name", db);
        node.put("update_seq", 0);
        node.put("instance_start_time", FAKE_INSTANCE_START_TIME);
        return node;
    }

    @POST
    @Path("/_missing_revs")
    public ObjectNode missingRevs(final JsonNode missingRevsRequest) throws IOException {
        final Directory dir = DATABASES.get(db);
        if (dir == null) {
            throw new WebApplicationException(404);
        }
        final Iterator<Entry<String, JsonNode>> it = missingRevsRequest.getFields();
        final ObjectNode missingRevsResponse = MAPPER.createObjectNode();
        final IndexReader reader = IndexReader.open(dir, true);
        final TermDocs termDocs = reader.termDocs();
        while (it.hasNext()) {
            final Entry<String, JsonNode> idAndRevs = it.next();
            termDocs.seek(new Term("_id", idAndRevs.getKey()));
            if (!termDocs.next()) {
                missingRevsResponse.put(idAndRevs.getKey(), idAndRevs.getValue());
            }
        }
        termDocs.close();
        reader.close();

        final ObjectNode response = MAPPER.createObjectNode();
        response.put("missing_revs", missingRevsResponse);
        return response;
    }

    @PUT
    @Path("/{id}")
    public Response updateDocument(@PathParam("id") final String id) {
        final ObjectNode node = MAPPER.createObjectNode();
        node.put("ok", true);
        node.put("id", id);
        node.put("rev", FAKE_REV);
        return Response.status(201).entity(node).build();
    }

    // For CouchDB 1.1
    @PUT
    @Path("/_design/{id}")
    @Consumes("multipart/related")
    public Response updateDesignDocument(@PathParam("id") final String id) {
        return updateDocument("_design/" + id);
    }

    // For CouchDB 1.1
    @PUT
    @Path("/_local/{id}")
    @Consumes("multipart/related")
    public Response updateLocalDocument(@PathParam("id") final String id) {
        return updateDocument("_local/" + id);
    }

    @PUT
    @Path("/{id}")
    @Consumes("multipart/related")
    public Response updateDocumentAndAttachments(@PathParam("id") final String id, final MultiPart body)
            throws IOException, SAXException, TikaException {
        final Directory dir = getDirectory(db);
        final IndexWriter writer = writer(dir);
        writer.updateDocument(new Term("_id", id), new Document());
        writer.close();

        final Tika tika = new Tika();
        for (final BodyPart bodyPart : body.getBodyParts()) {
            final BodyPartEntity entity = (BodyPartEntity) bodyPart.getEntity();
            final Metadata metadata = new Metadata();
            final String txt = tika.parseToString(entity.getInputStream(), metadata);
            // System.err.println(metadata);
            // System.err.println(txt);
        }

        final ObjectNode node = MAPPER.createObjectNode();
        node.put("ok", true);
        node.put("id", id);
        node.put("rev", FAKE_REV);
        return Response.status(201).entity(node).build();
    }

    private Directory getDirectory(final String db) {
        final Directory result = DATABASES.get(db);
        if (result == null) {
            throw new WebApplicationException(404);
        }
        return result;
    }

    private IndexWriter writer(final Directory dir) throws IOException {
        return new IndexWriter(dir, config());
    }

    private IndexWriterConfig config() {
        return new IndexWriterConfig(_VERSION, ANALYZER);
    }

}
