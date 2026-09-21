package fi.csc.avaa.paituli.rest;

import java.util.Map;

import fi.csc.avaa.paituli.constants.Constants;
import fi.csc.avaa.paituli.model.BuildInfo;
import fi.csc.avaa.paituli.service.BuildInfoService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path(Constants.PATH_BUILD_INFO)
@Produces(MediaType.APPLICATION_JSON)
public class BuildInfoResource {

    @Inject
    BuildInfoService buildInfoService;

    // Returns the build info written at installation time
    @GET
    public Response buildInfo() {
        BuildInfo buildInfo = buildInfoService.getBuildInfo();
        if (buildInfo == null) {
            return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity(Map.of("message", "Build info is not available"))
                    .build();
        }
        return Response.ok(buildInfo).build();
    }
}
