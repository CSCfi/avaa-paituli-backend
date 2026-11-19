package fi.csc.avaa.paituli.rest;

import fi.csc.avaa.paituli.service.DownloadService;
import io.quarkus.vertx.http.ManagementInterface;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

// Lets a planned restart wait for the jobs already accepted instead of
// cancelling them: POST /drain stops new jobs, and GET /drain reports how
// many jobs and downloads to wait for, so the restart can wait for 0 and
// then stop the service. Both are on the management interface, which
// listens on localhost only, so they aren't reachable from outside the
// server.
@ApplicationScoped
public class DrainRoutes {

    @Inject
    DownloadService downloadService;

    void register(@Observes ManagementInterface management) {
        management.router().post("/drain").handler(context -> {
            downloadService.drain();
            respond(context);
        });
        management.router().get("/drain").handler(this::respond);
    }

    private void respond(RoutingContext context) {
        context.json(new JsonObject()
            .put("draining", downloadService.draining())
            .put("waitingFor", downloadService.waitingFor()));
    }
}
