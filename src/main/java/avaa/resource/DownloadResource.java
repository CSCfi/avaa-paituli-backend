package avaa.resource;

import avaa.model.DownloadRequest;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/download")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DownloadResource {

    @POST
    public Response generateDownload(@Valid DownloadRequest downloadRequest) {
        return Response.ok().build();
    }
}
