package fi.csc.avaa.paituli.rest;

import fi.csc.avaa.paituli.model.DownloadRequest;
import fi.csc.avaa.paituli.service.DownloadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.core.Response;

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
        Response response = downloadResource.generateDownload(new DownloadRequest());

        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Boolean> entity = (Map<String, Boolean>) response.getEntity();
        assertThat(entity.get("success")).isTrue();
    }
}
