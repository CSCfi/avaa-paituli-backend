package fi.csc.avaa.paituli.rest;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.model.DownloadJob;
import fi.csc.avaa.paituli.model.DownloadRequest;
import fi.csc.avaa.paituli.model.TestJobs;
import fi.csc.avaa.paituli.service.DownloadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class DownloadResourceTest {

    @Mock
    DownloadService downloadService;

    @InjectMocks
    DownloadResource downloadResource;

    @Test
    public void shouldReturnStatus200AndJsonObject() {

        // Dummy request and job
        DownloadJob job = TestJobs.job(DownloadType.ZIP);
        DownloadRequest request = job.request;

        // Mock job creation
        Mockito.when(downloadService.createDownloadJob(request))
            .thenReturn(job);

        Response response = downloadResource.generateDownload(request);

        // Check that the service returned OK and some JSON with expected contents
        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertThat(entity.get("message")).isEqualTo("Job created");
    }

    @Test
    public void shouldReportFailureWhenErrorHasNoMessage() {
        DownloadJob job = TestJobs.job(DownloadType.ZIP);

        // DownloadGenerator assigns err.getMessage(), which is null for any
        // exception constructed without one (e.g. a bare NPE).
        job.error = null;
        job.progress = 1.0;

        Mockito.when(downloadService.getJob(job.ID)).thenReturn(job);

        Response response = downloadResource.serveOutput(job.ID);

        // The job failed, so it must be reported as a failure rather than
        // throwing while building the response.
        assertThat(response.getStatus()).isEqualTo(500);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertThat(entity.get("error")).isNotNull();
    }

    @Test
    public void cancelShouldBeDelegatedToTheService() {
        DownloadJob job = TestJobs.job(DownloadType.ZIP);

        Mockito.when(downloadService.getJob(job.ID)).thenReturn(job);
        Mockito.when(downloadService.cancel(job)).thenReturn(true);

        Response response = downloadResource.cancelDownload(job.ID);

        // Whether a job may be cancelled is the service's decision
        Mockito.verify(downloadService).cancel(job);
        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertThat(entity.get("message")).isEqualTo("Job will be cancelled");
    }

    @Test
    public void cancelRefusedByTheServiceShouldStillSucceed() {
        DownloadJob job = TestJobs.job(DownloadType.ZIP);

        Mockito.when(downloadService.getJob(job.ID)).thenReturn(job);
        Mockito.when(downloadService.cancel(job)).thenReturn(false);

        Response response = downloadResource.cancelDownload(job.ID);

        // Cancelling a job that already stopped is a no-op, not an error
        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertThat(entity.get("message")).isEqualTo("Job is no longer processing");
    }

    @Test
    public void statusShouldReportCancellation() {
        DownloadJob job = TestJobs.job(DownloadType.ZIP);

        // processJob never sets progress to 1.0 for a cancelled job, so without
        // an explicit flag the client sees a job frozen part-way forever.
        job.progress = 0.4;
        job.cancelled = true;

        Mockito.when(downloadService.pollJob(job.ID)).thenReturn(job);

        Response response = downloadResource.getStatus(job.ID);

        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertThat(entity.get("cancelled")).isEqualTo(true);
    }

    @Test
    public void unknownJobShouldBeNotFoundOnEveryEndpoint() {
        Mockito.when(downloadService.pollJob("no-such-job")).thenReturn(null);
        Mockito.when(downloadService.getJob("no-such-job")).thenReturn(null);

        assertThat(downloadResource.getStatus("no-such-job").getStatus()).isEqualTo(404);
        assertThat(downloadResource.cancelDownload("no-such-job").getStatus()).isEqualTo(404);
        assertThat(downloadResource.serveOutput("no-such-job").getStatus()).isEqualTo(404);
    }

    @Test
    public void serveOutputShouldConflictWhileStillProcessing() {
        DownloadJob job = TestJobs.job(DownloadType.ZIP);
        job.progress = 0.5;

        Mockito.when(downloadService.getJob(job.ID)).thenReturn(job);

        Response response = downloadResource.serveOutput(job.ID);

        assertThat(response.getStatus()).isEqualTo(409);
    }

    @Test
    public void serveOutputShouldReportGoneWhenOutputWasCleaned() {
        DownloadJob job = TestJobs.job(DownloadType.ZIP);

        // Finished successfully, but nothing exists at outputFilePath
        job.progress = 1.0;

        Mockito.when(downloadService.getJob(job.ID)).thenReturn(job);

        Response response = downloadResource.serveOutput(job.ID);

        assertThat(response.getStatus()).isEqualTo(410);
    }

    @Test
    public void serveOutputShouldAttachOutputFilename(@TempDir Path tempDir) throws IOException {
        DownloadJob job = TestJobs.job(DownloadType.ZIP, tempDir.toString());

        job.progress = 1.0;
        Files.createFile(Path.of(job.outputFilePath));

        Mockito.when(downloadService.getJob(job.ID)).thenReturn(job);

        Response response = downloadResource.serveOutput(job.ID);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaderString("Content-Disposition"))
            .isEqualTo("attachment; filename=\"" + job.outputFilename + "\"");
    }
}
