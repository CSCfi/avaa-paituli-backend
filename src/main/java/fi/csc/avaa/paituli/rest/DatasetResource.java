package fi.csc.avaa.paituli.rest;

import fi.csc.avaa.paituli.constants.Constants;
import fi.csc.avaa.paituli.model.LocalizedDataset;
import fi.csc.avaa.paituli.service.DatasetService;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path(Constants.PATH_DATASETS)
public class DatasetResource {

    @Inject
    DatasetService datasetService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<LocalizedDataset> datasets() {
        return datasetService.getLocalizedDatasets(Constants.LOCALE_FI);
    }
}
