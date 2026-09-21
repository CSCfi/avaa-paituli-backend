package fi.csc.avaa.paituli.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fi.csc.avaa.paituli.model.BuildInfo;

public class BuildInfoServiceTest {

    @TempDir
    Path tempDir;

    private final BuildInfoService service = new BuildInfoService();

    @Test
    public void shouldReadBuildInfo() throws IOException {
        Path file = tempDir.resolve("build-info.json");
        Files.writeString(file, """
                {
                    "branch": "staging",
                    "builtAt": "2026-09-16T09:12:33Z",
                    "commit": "a1b2c3d4e5f6"
                }
                """);

        BuildInfo buildInfo = service.readBuildInfo(file);

        assertThat(buildInfo).isNotNull();
        assertThat(buildInfo.branch).isEqualTo("staging");
        assertThat(buildInfo.builtAt).isEqualTo("2026-09-16T09:12:33Z");
        assertThat(buildInfo.commit).isEqualTo("a1b2c3d4e5f6");
    }

    @Test
    public void shouldReturnNullWhenFileIsMissing() {
        assertThat(service.readBuildInfo(tempDir.resolve("nonexistent.json"))).isNull();
    }

    @Test
    public void shouldReturnNullWhenFileIsNotValidJson() throws IOException {
        Path file = tempDir.resolve("build-info.json");
        Files.writeString(file, "not json at all");

        assertThat(service.readBuildInfo(file)).isNull();
    }
}
