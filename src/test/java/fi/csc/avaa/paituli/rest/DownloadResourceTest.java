package fi.csc.avaa.paituli.rest;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.model.DownloadJob;
import fi.csc.avaa.paituli.model.DownloadRequest;
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

    private final String outputPath = "/bar";
    private final String filePrefix = "batman_";

    @Test
    public void shouldReturnStatus200AndJsonObject() {

        // Dummy request and job
        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;
        DownloadJob job = new DownloadJob(request, filePrefix, outputPath);

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

    // Shorthand for a job that is not tied to a real output directory
    private DownloadJob dummyJob() {
        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;
        return new DownloadJob(request, filePrefix, outputPath);
    }

    @Test
    public void shouldReportFailureWhenErrorHasNoMessage() {
        DownloadJob job = dummyJob();

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
    public void cancellingCompletedJobShouldNotDestroyItsOutput(@TempDir Path tempDir)
            throws IOException {
        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;
        DownloadJob job = new DownloadJob(request, filePrefix, tempDir.toString());

        // The job finished and its output is on disk
        job.progress = 1.0;
        Files.createFile(Path.of(job.outputFilePath));

        Mockito.when(downloadService.getJob(job.ID)).thenReturn(job);

        downloadResource.cancelDownload(job.ID);

        // A cancel arriving after completion must not deny access to a package
        // that is complete and still on disk.
        Response response = downloadResource.serveOutput(job.ID);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void statusShouldReportCancellation() {
        DownloadJob job = dummyJob();

        // processJob never sets progress to 1.0 for a cancelled job, so without
        // an explicit flag the client sees a job frozen part-way forever.
        job.progress = 0.4;
        job.cancelled = true;

        Mockito.when(downloadService.getJob(job.ID)).thenReturn(job);

        Response response = downloadResource.getStatus(job.ID);

        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertThat(entity.get("cancelled")).isEqualTo(true);
    }
}
