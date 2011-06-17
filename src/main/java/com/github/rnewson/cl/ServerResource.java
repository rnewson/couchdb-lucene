package com.github.rnewson.cl;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;

import com.sun.jersey.spi.resource.Singleton;

@Path("/")
@Produces("application/json")
@Consumes("application/json")
public final class ServerResource {

    private final File indexDirectory = new File("target/indexes"); // Inject
                                                                    // somehow.
    private final Map<String, IndexWriter> writers = new HashMap<String, IndexWriter>();

    @GET
    @Path("/")
    public JsonNode getServerVersion() {
        final ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("couchdb-lucene", "Welcome");
        response.put("version", this.getClass().getPackage().getImplementationVersion());
        return response;
    }

    @PUT
    @Path("{db}")
    public Response createDatabase(@PathParam("db") final String db) throws IOException {
        final Directory dir = FSDirectory.open(new File(indexDirectory, db));
        if (IndexReader.indexExists(dir)) {
            throw new CouchException.DatabaseAlreadyExistsException();
        }
        final IndexWriter writer = openDatabase(dir);
        writer.close();
        return Response.created("/" + db).build();
    }

    @DELETE
    public Response deleteDatabase() throws IOException {
        final Directory dir = DATABASES.remove(db);
        if (dir == null) {
            throw new WebApplicationException(404);
        }
        for (final String name : dir.listAll()) {
            dir.deleteFile(name);
        }
        return Response.status(200).build();
    }

}
