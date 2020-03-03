package avaa.resource;

import avaa.model.DownloadRequest;
import avaa.model.Result;

import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Set;

@Path("/download")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DownloadResource {

    @Inject
    Validator validator;

    @POST
    public Result generateDownload(DownloadRequest downloadRequest) {
        Set<ConstraintViolation<DownloadRequest>> violations = validator.validate(downloadRequest);
        if (violations.isEmpty()) {
            return new Result("success");
        } else {
            return new Result(violations);
        }
    }
}
