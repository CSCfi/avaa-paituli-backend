package fi.csc.avaa.paituli.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import fi.csc.avaa.paituli.model.BuildInfo;
import fi.csc.avaa.paituli.service.BuildInfoService;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
public class BuildInfoResourceTest {

    @Mock
    BuildInfoService buildInfoService;

    @InjectMocks
    BuildInfoResource resource;

    @Test
    public void shouldReturnBuildInfo() {
        BuildInfo buildInfo = new BuildInfo();
        buildInfo.branch = "staging";
        buildInfo.builtAt = "2026-09-16T09:12:33Z";
        buildInfo.commit = "a1b2c3d4e5f6";
        Mockito.when(buildInfoService.getBuildInfo()).thenReturn(buildInfo);

        Response response = resource.buildInfo();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isSameAs(buildInfo);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldReturn404WhenBuildInfoIsNotAvailable() {
        Mockito.when(buildInfoService.getBuildInfo()).thenReturn(null);

        Response response = resource.buildInfo();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat((Map<String, Object>) response.getEntity())
                .containsEntry("message", "Build info is not available");
    }
}
