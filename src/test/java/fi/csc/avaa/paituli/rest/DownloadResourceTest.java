package fi.csc.avaa.paituli.rest;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.model.DownloadJob;
import fi.csc.avaa.paituli.model.DownloadRequest;
import fi.csc.avaa.paituli.service.DownloadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.core.Response;

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
}
