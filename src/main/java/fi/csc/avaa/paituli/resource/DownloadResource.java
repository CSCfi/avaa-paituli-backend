package fi.csc.avaa.paituli.resource;

import fi.csc.avaa.paituli.constants.Constants;
import fi.csc.avaa.paituli.model.DownloadRequest;
import fi.csc.avaa.paituli.service.DownloadService;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path(Constants.PATH_DOWNLOAD)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DownloadResource {

    @Inject
    DownloadService downloadService;

    @POST
    public Response generateDownload(@Valid DownloadRequest downloadRequest) {
        downloadService.generateDownload(downloadRequest);
        return Response.ok().build();
    }
}
