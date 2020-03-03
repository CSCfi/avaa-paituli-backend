package avaa.resource;

import avaa.entity.Dataset;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/getDatasets")
public class DatasetResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Dataset> paituliDatasets() {
        return Dataset.listAll();
    }
}