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

    // Creates and stores a new download job. Processing waits for the job's
    // first status poll, so no work is done for a client that never shows up.
    public DownloadJob createDownloadJob(DownloadRequest request) {
        DownloadJob job = new DownloadJob(request, filePrefix, outputPath);
        jobs.put(job.ID, job);
        return job;
    }

    // Looks up a job for its client's status poll. The first poll starts
    // processing; later ones only keep the job alive.
    public DownloadJob pollJob(String jobId) {
        DownloadJob job = getJob(jobId);
        if (job != null) start(job);
        return job;
    }

    // Starts processing at most once, and never for a job cancelled before it started
    private void start(DownloadJob job) {
        if (job.cancelled || !job.started.compareAndSet(false, true)) return;

        // Alternatively, here we could implement scheduling, priority, etc.
        managedExecutor.runAsync(() -> {
            downloadGenerator.processJob(job);
        });
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
        // A job cancelled before it started has no output and nothing to
        // report, so there is no reason to keep it
        jobs.values().removeIf(job -> job.cancelled && !job.started.get());
    }
}