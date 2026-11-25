package fi.csc.avaa.paituli.service;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

import fi.csc.avaa.paituli.download.DownloadGenerator;
import fi.csc.avaa.paituli.download.DownloadRequestException;
import fi.csc.avaa.paituli.model.DownloadJob;
import fi.csc.avaa.paituli.model.DownloadRequest;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

// Created at startup rather than on first use, so a bad config stops the
// service from starting
@Startup
@ApplicationScoped
public class DownloadService {

    private static final Logger LOG = Logger.getLogger(DownloadService.class);

    // What the client is told about a job that failed for any reason other
    // than its request. The details are in the log, under the job's ID.
    static final String SERVER_ERROR = "The download failed because of a server error";
    
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

    // How long a drain waits for its jobs, after which the restart it
    // prepares for goes ahead and cancels the jobs left
    @ConfigProperty(name = "paituli.download.jobDrainTimeout")
    Duration jobDrainTimeout;

    // How many jobs may be processed at once; the rest wait for their turn
    @ConfigProperty(name = "paituli.download.maxConcurrentJobs")
    int maxConcurrentJobs;

    // Runs at most maxConcurrentJobs jobs at a time and queues the rest in
    // order. Built in init() rather than injected, since the cap comes from config.
    ManagedExecutor managedExecutor;

    @PostConstruct
    void init() {
        // Below 1 there would be no slots at all, and -1 would lift the cap
        if (maxConcurrentJobs < 1) {
            throw new IllegalStateException(
                "paituli.download.maxConcurrentJobs must be at least 1, was " + maxConcurrentJobs);
        }
        managedExecutor = ManagedExecutor.builder()
            .maxAsync(maxConcurrentJobs)
            .maxQueued(-1)
            .build();
    }

    // Logs the effective config once at startup, so a deploy shows what the
    // environment actually set
    void onStart(@Observes StartupEvent event) {
        LOG.infof("Writing downloads to %s, at most %d jobs at once, cancelling jobs unseen for %d s",
            outputPath, maxConcurrentJobs, keepAliveTimeout.toSeconds());
    }

    // Set by drain() and never cleared: the restart that follows resets it
    private volatile boolean draining;

    // When the drain started, as System.nanoTime()
    private volatile long drainStartedAt;

    // Prepares for a planned restart: no new jobs are accepted from here on,
    // while the ones already accepted run to the end and their clients start
    // downloading the output. The restart waits until waitingFor() is 0, and
    // its stop then cancels whatever is left. The downloads themselves are
    // left to the web server in front, which the restart waits for
    // separately. Calling it again doesn't restart the timeout.
    public void drain() {
        if (draining) return;
        drainStartedAt = System.nanoTime();
        draining = true;
        LOG.infof("Draining for a restart: accepting no new jobs, waiting up to %d s for %d jobs"
                + " to finish and their downloads to start",
            jobDrainTimeout.toSeconds(), waitingFor());
    }

    public boolean draining() {
        return draining;
    }

    // Counts what a drain waits for: jobs running, queued, or waiting for
    // their first poll, and finished jobs whose client hasn't requested the
    // output yet. Abandoned finished jobs drop out after the keep-alive
    // timeout, so the count reaches 0 even if their clients have left.
    // Past jobDrainTimeout it is 0 regardless.
    public long waitingFor() {
        return waitingFor(System.nanoTime());
    }

    long waitingFor(long now) {
        if (draining && now - drainStartedAt > jobDrainTimeout.toNanos()) return 0;

        long keepAlive = keepAliveTimeout.toNanos();
        long waiting = 0;
        for (DownloadJob job : jobs.values()) {
            if (job.cancelled || job.failed()) continue;
            if (job.processing() || (!job.fetched && now - job.lastSeen <= keepAlive)) waiting++;
        }
        return waiting;
    }

    // Stops the jobs when the application shuts down. This runs before the
    // rest of the application is torn down, so a job's last steps, such as
    // its statistics write, still work.
    void onStop(@Observes ShutdownEvent event) {
        shutdown();
    }

    // How long shutdown waits for cancelled jobs to stop before interrupting them
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);

    // Set on shutdown, so no new job starts and a job it interrupts isn't
    // logged as a failure
    private volatile boolean shuttingDown;

    // A planned restart drains first, so this meets running jobs only when
    // the drain timed out or the service is stopped without draining.
    // Jobs don't survive a restart anyway. Started ones are cancelled, which
    // stops a running job within one chunk of copying and a queued one
    // before it starts. An interrupt doesn't reliably stop a copy, so it is
    // only the fallback for jobs that don't stop in time.
    void shutdown() {
        int lost = 0;
        // Shared with start(), so a job starts either before this looks for
        // jobs to cancel or not at all
        synchronized (this) {
            shuttingDown = true;
            for (DownloadJob job : jobs.values()) {
                if (job.started.get() && cancel(job, "service shutting down")) lost++;
            }
            managedExecutor.shutdown();
        }
        if (lost > 0) {
            LOG.warnf("Unfinished jobs lost in shutdown: %d", lost);
        }

        boolean stopped = false;
        try {
            stopped = managedExecutor.awaitTermination(STOP_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!stopped) {
            LOG.warnf("Jobs still running %d s into shutdown, interrupting them", STOP_TIMEOUT.toSeconds());
            managedExecutor.shutdownNow();
        }
    }

    // Job storage (could later move to DB/Redis)
    private final Map<String, DownloadJob> jobs = new ConcurrentHashMap<>();

    // Creates and stores a new download job. Processing waits for the job's
    // first status poll, so a client that never polls never takes up a slot,
    // not even for the time it takes the keep-alive sweep to cancel it.
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
    private synchronized void start(DownloadJob job) {
        if (job.cancelled || shuttingDown || !job.started.compareAndSet(false, true)) return;

        // While maxConcurrentJobs are running, the job waits in the executor's
        // queue with progress at 0. A job cancelled while waiting never runs.
        long queuedAt = System.nanoTime();
        managedExecutor.runAsync(() -> run(job, queuedAt));
    }

    // Runs a job on a worker thread, and records and logs its failure. This
    // catches everything, Errors included: anything thrown out of here would
    // vanish unlogged with the future runAsync returns.
    private void run(DownloadJob job, long queuedAt) {
        if (job.cancelled) return;
        long started = System.nanoTime();
        LOG.infof("%s Started after %s in queue", job, seconds(started - queuedAt));
        try {
            downloadGenerator.processJob(job);
        } catch (Throwable err) {
            if (shuttingDown && job.cancelled) {
                // shutdown() cancelled it and then interrupted it, which made
                // the copy fail, and has logged the loss. Other failures during
                // shutdown are logged as usual.
                LOG.infof("%s Interrupted by shutdown", job);
                return;
            }
            // getMessage() is null for exceptions constructed without one
            String reason = err.getMessage() != null ? err.getMessage() : err.toString();
            // The client is told only what was wrong with its request. Any
            // other message may describe the server, e.g. its file paths.
            String clientError;
            if (err instanceof DownloadRequestException) {
                LOG.warnf("%s Failed: %s", job, reason);
                clientError = reason;
            } else {
                LOG.errorf(err, "%s Failed: %s", job, reason);
                clientError = SERVER_ERROR;
            }
            // A job left looking like it is still processing would keep its
            // client waiting indefinitely, as the client's polls keep it alive.
            // A job that already completed keeps its output.
            if (job.processing()) job.error = clientError;
            return;
        }
        if (job.cancelled) {
            // cancel() has logged why
            LOG.debugf("%s Stopped after cancellation", job);
        } else {
            // The total counts from when the job was received, so it also
            // includes the wait for the client's first poll
            LOG.infof("%s Completed in %s, %s of it in queue", job,
                seconds(System.nanoTime() - job.receivedAt), seconds(started - queuedAt));
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
        jobs.values().removeIf(job -> {
            if (!job.cancelled || job.started.get()) return false;
            LOG.debugf("%s Forgotten, as it was cancelled before it started", job);
            return true;
        });
    }
}