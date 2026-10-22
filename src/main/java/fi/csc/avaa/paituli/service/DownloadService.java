package fi.csc.avaa.paituli.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

import fi.csc.avaa.paituli.download.DownloadGenerator;
import fi.csc.avaa.paituli.model.DownloadJob;
import fi.csc.avaa.paituli.model.DownloadRequest;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DownloadService {

    private static final Logger LOG = Logger.getLogger(DownloadService.class);
    
    @Inject
    DownloadGenerator downloadGenerator;

    @Inject
    ManagedExecutor managedExecutor;

    @ConfigProperty(name = "paituli.download.filePrefix")
    public String filePrefix;

    @ConfigProperty(name = "paituli.download.outputPath")
    public String outputPath;

    // A processing job that no client has asked about for this long is
    // assumed abandoned (e.g. the user left the frontend) and gets cancelled
    @ConfigProperty(name = "paituli.download.keepAliveTimeout")
    Duration keepAliveTimeout;


    // Job storage (could later move to DB/Redis)
    private final Map<String, DownloadJob> jobs = new ConcurrentHashMap<>();

    // Creates a new download job, starts its thread and return the job 
    public DownloadJob createDownloadJob(DownloadRequest request) {

        // Create a job for the request and store it
        DownloadJob job = new DownloadJob(request, filePrefix, outputPath);
        jobs.put(job.ID, job);

        // Start processing immediately.
        // Alternatively, here we could implement scheduling, priority, etc.
        managedExecutor.runAsync(() -> {
            downloadGenerator.processJob(job);
        });
        return job;
    }

    // Looks up a job for a client. Any request about a job is a sign of life
    // from its client, which keeps the job from being cancelled as abandoned.
    public DownloadJob getJob(String jobId) {
        DownloadJob job = jobs.get(jobId);
        if (job != null) job.lastSeen = System.nanoTime();
        return job;
    }

    // Marks a job for cancellation and returns whether it was marked.
    // Cancelling a job that has already stopped is a no-op: for a completed
    // job it would deny access to a package that is still on disk, and for
    // a failed one it would hide the failure behind a cancellation.
    public boolean cancel(DownloadJob job) {
        if (job.cancelled || !job.processing() || job.failed()) return false;
        job.cancelled = true;
        return true;
    }

    // With the default timeout, an abandoned job is cancelled 15-20 s after
    // its client was last seen
    @Scheduled(every = "5s")
    void cancelAbandonedJobs() {
        cancelAbandonedJobs(System.nanoTime());
    }

    void cancelAbandonedJobs(long now) {
        long timeout = keepAliveTimeout.toNanos();
        for (DownloadJob job : jobs.values()) {
            if (now - job.lastSeen > timeout && cancel(job)) {
                LOG.infof("%s Cancelled, client not seen for over %s", job, keepAliveTimeout);
            }
        }
    }
}