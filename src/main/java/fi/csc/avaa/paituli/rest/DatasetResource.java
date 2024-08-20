package fi.csc.avaa.paituli.rest;

import fi.csc.avaa.paituli.constants.Constants;
import fi.csc.avaa.paituli.model.LocalizedDataset;
import fi.csc.avaa.paituli.service.DatasetService;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Locale;

@Path(Constants.PATH_DATASETS)
public class DatasetResource {

    @Inject
    DatasetService datasetService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<LocalizedDataset> datasets(@PathParam("locale") String locale) {
        return datasetService.getLocalizedDatasets(Locale.forLanguageTag(locale));
    }
}
