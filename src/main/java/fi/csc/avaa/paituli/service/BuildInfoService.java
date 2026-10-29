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

    // A failed read is logged at WARN or ERROR the first time and at DEBUG
    // after that, since it is retried on every request
    private volatile boolean failureLogged;

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
            LOG.logf(failureLevel(Logger.Level.WARN),
                "Build info file %s is missing or not readable", path.toAbsolutePath());
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path); Jsonb jsonb = JsonbBuilder.create()) {
            return jsonb.fromJson(reader, BuildInfo.class);
        } catch (Exception e) {
            LOG.logf(failureLevel(Logger.Level.ERROR), e,
                "Could not read build info from %s", path.toAbsolutePath());
            return null;
        }
    }

    // The level for logging a failed read: the given one the first time,
    // DEBUG after that
    private Logger.Level failureLevel(Logger.Level first) {
        if (failureLogged) return Logger.Level.DEBUG;
        failureLogged = true;
        return first;
    }
}
