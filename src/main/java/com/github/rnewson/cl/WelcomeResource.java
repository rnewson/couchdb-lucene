package com.github.rnewson.cl;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;

@Path("/")
@Produces("application/json")
@Consumes("application/json")
public class WelcomeResource {

    @GET
    public String welcome() {
        final Package p = this.getClass().getPackage();
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("couchdb-lucene", "Welcome");
        node.put("version", p.getImplementationVersion());
        return node.toString();
    }

}
