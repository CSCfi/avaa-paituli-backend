package fi.csc.avaa.paituli.service;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

import fi.csc.avaa.paituli.download.DownloadGenerator;
import fi.csc.avaa.paituli.model.DownloadJob;
import fi.csc.avaa.paituli.model.DownloadRequest;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DownloadService {

    private static final Logger LOG = Logger.getLogger(DownloadService.class);
    
    @Inject
    DownloadGenerator downloadGenerator;

    @ConfigProperty(name = "paituli.download.filePrefix")
    public String filePrefix;

    @ConfigProperty(name = "paituli.download.outputPath")
    public String outputPath;

    // A processing job that no client has asked about for this long is
    // assumed abandoned (e.g. the user left the frontend) and gets cancelled
    @ConfigProperty(name = "paituli.download.keepAliveTimeout")
    Duration keepAliveTimeout;

    // How many jobs may be processed at once; the rest wait for their turn
    @ConfigProperty(name = "paituli.download.maxConcurrentJobs")
    int maxConcurrentJobs;

    // Runs at most maxConcurrentJobs jobs at a time and queues the rest in
    // order. Built in init() rather than injected, since the cap comes from config.
    ManagedExecutor managedExecutor;

    @PostConstruct
    void init() {
        managedExecutor = ManagedExecutor.builder()
            .maxAsync(maxConcurrentJobs)
            .maxQueued(-1)
            .build();
    }

    // Jobs don't survive a restart anyway, so queued ones are dropped and
    // running ones interrupted
    @PreDestroy
    void shutdown() {
        managedExecutor.shutdownNow();
    }

    // Job storage (could later move to DB/Redis)
    private final Map<String, DownloadJob> jobs = new ConcurrentHashMap<>();

    // Creates and stores a new download job. Processing waits for the job's
    // first status poll, so no work is done for a client that never shows up.
    public DownloadJob createDownloadJob(DownloadRequest request) {
        DownloadJob job = new DownloadJob(request, filePrefix, outputPath);
        jobs.put(job.ID, job);
        // The full ID is what the client holds; later lines carry only its prefix
        LOG.infof("%s Received %s request for %s, ID %s, output %s",
            job, request.downloadType, request.data_id, job.ID, job.outputFilePath);
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

        // While maxConcurrentJobs are running, the job waits in the executor's
        // queue with progress at 0. A job cancelled while waiting never runs.
        managedExecutor.runAsync(() -> run(job));
    }

    // Runs a job on a worker thread, and records and logs its failure. This
    // catches everything, Errors included: anything thrown out of here would
    // vanish unlogged with the future runAsync returns.
    private void run(DownloadJob job) {
        if (job.cancelled) return;
        long started = System.nanoTime();
        LOG.infof("%s Started after %s in queue", job, seconds(started - job.receivedAt));
        try {
            downloadGenerator.processJob(job);
        } catch (Throwable err) {
            // getMessage() is null for exceptions constructed without one
            String reason = err.getMessage() != null ? err.getMessage() : err.toString();
            LOG.errorf(err, "%s Failed: %s", job, reason);
            // A job left looking like it is still processing would keep its
            // client waiting indefinitely, as the client's polls keep it alive.
            // A job that already completed keeps its output.
            if (job.processing()) job.error = reason;
            return;
        }
        if (job.cancelled) {
            // cancel() has logged why
            LOG.debugf("%s Stopped after cancellation", job);
        } else {
            LOG.infof("%s Completed in %s, %s of it in queue", job,
                seconds(System.nanoTime() - job.receivedAt), seconds(started - job.receivedAt));
        }
    }

    // Formats a System.nanoTime() difference, e.g. "42.3 s"
    private static String seconds(long nanos) {
        return String.format(Locale.ROOT, "%.1f s", nanos / 1e9);
    }

    // Looks up a job for a client. Any request about a job is a sign of life
    // from its client, which keeps the job from being cancelled as abandoned.
    public DownloadJob getJob(String jobId) {
        DownloadJob job = jobs.get(jobId);
        if (job != null) job.lastSeen = System.nanoTime();
        return job;
    }

    // Marks a job for cancellation and returns whether it was marked. Logs
    // the decision with the caller's reason, so every cancellation is logged
    // in one place and one format.
    // Cancelling a job that has already stopped is a no-op: for a completed
    // job it would deny access to a package that is still on disk, and for
    // a failed one it would hide the failure behind a cancellation.
    public boolean cancel(DownloadJob job, String reason) {
        if (job.cancelled || !job.processing() || job.failed()) return false;
        job.cancelled = true;
        LOG.infof("%s Cancelled: %s", job, reason);
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
        String reason = String.format("client not seen for %d s", keepAliveTimeout.toSeconds());
        for (DownloadJob job : jobs.values()) {
            if (now - job.lastSeen > timeout) cancel(job, reason);
        }
        // A job cancelled before it started has no output and nothing to
        // report, so there is no reason to keep it
        jobs.values().removeIf(job -> job.cancelled && !job.started.get());
    }
}