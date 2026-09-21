package fi.csc.avaa.paituli.service;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import fi.csc.avaa.paituli.model.BuildInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

@ApplicationScoped
public class BuildInfoService {

    private static final Logger LOG = Logger.getLogger(BuildInfoService.class);

    @ConfigProperty(name = "paituli.buildInfo.path")
    String buildInfoPath;

    // The file is written at installation time and does not change while the
    // service runs, so the first successful read is kept for the process
    // lifetime. A failed read is not cached: the file may still appear later.
    private volatile BuildInfo buildInfo;

    // Returns the build info, or null if the file is missing or unreadable.
    public BuildInfo getBuildInfo() {
        BuildInfo cached = buildInfo;
        if (cached == null) {
            cached = readBuildInfo(Paths.get(buildInfoPath));
            buildInfo = cached;
        }
        return cached;
    }

    BuildInfo readBuildInfo(Path path) {
        if (!Files.isReadable(path)) {
            LOG.warnf("Build info file %s is missing or not readable", path.toAbsolutePath());
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path); Jsonb jsonb = JsonbBuilder.create()) {
            return jsonb.fromJson(reader, BuildInfo.class);
        } catch (Exception e) {
            LOG.errorf(e, "Could not read build info from %s", path.toAbsolutePath());
            return null;
        }
    }
}
