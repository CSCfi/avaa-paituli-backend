package fi.csc.avaa.paituli.rest;

import fi.csc.avaa.paituli.constants.Constants;
import fi.csc.avaa.paituli.entity.Dataset;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path(Constants.PATH_DATASETS)
public class DatasetResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Dataset> paituliDatasets() {
        return Dataset.listAll();
    }
}
