package fi.csc.avaa.paituli.rest;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import fi.csc.avaa.paituli.constants.Constants;
import fi.csc.avaa.paituli.model.DownloadJob;
import fi.csc.avaa.paituli.model.DownloadRequest;
import fi.csc.avaa.paituli.service.DownloadService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path(Constants.PATH_DOWNLOAD)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DownloadResource {

    @Inject
    DownloadService downloadService;

    @ConfigProperty(name = "paituli.download.outputPath")
    String outputPath;

    // A shorthand method to build responses
    private Map<String, Object> jobResponse(DownloadJob job, String message) {
        return Map.of(
            "message", message,
            "ID", job.ID,
            "progress", job.progress,
            "error", job.error
        );
    }

    // Starts a new download job 
    @POST
    public Response generateDownload(@Valid DownloadRequest downloadRequest) {
        DownloadJob job = downloadService.createDownloadJob(downloadRequest);
        return Response.ok(jobResponse(job, "Job created")).build();
    }

    // Returns the status of a download job 
    @GET
    @Path("/status/{jobId}")
    public Response getStatus(@PathParam("jobId") String jobId) {
        DownloadJob job = downloadService.getJob(jobId);
        if (job == null) return Response.status(Response.Status.NOT_FOUND).build();

        // Job errors are included in the response 
        return Response.ok(jobResponse(job, "")).build();
    }

    // Serves the output of a (completed) job for download 
    @GET
    @Path("/{jobId}")
    public Response serveOutput(@PathParam("jobId") String jobId) {
        DownloadJob job = downloadService.getJob(jobId);
        if (job == null) return Response.status(Response.Status.NOT_FOUND).build();

        if (!job.error.isEmpty()) {
            // Something went wrong during the processing
            return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(jobResponse(job, "Job failed"))
                .build();
        }

        if (job.processing()) {
            // Job has not finished, so there is nothing to download
            return Response
                .status(Response.Status.CONFLICT)
                .entity(jobResponse(job, "Download is still processing"))
                .build();
        }

        java.nio.file.Path output = Paths.get(job.outputFilePath);
        if (!Files.exists(output)) {
            // Job still exists but the output has been cleaned
            return Response
                .status(Response.Status.GONE)
                .entity(jobResponse(job, "Download not available anymore. You must start a new download."))
                .build();
        }

        // All good for download
        return Response
            .ok(output.toFile())
            .header(
                "Content-Disposition",
                "attachment; filename=\"" + job.outputFilename + "\"")
            .build();
    }

}
