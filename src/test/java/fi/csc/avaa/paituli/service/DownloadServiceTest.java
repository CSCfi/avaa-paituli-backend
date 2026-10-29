package fi.csc.avaa.paituli.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import fi.csc.avaa.paituli.LogCapture;
import fi.csc.avaa.paituli.LogCapture.Line;
import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.download.DownloadGenerator;
import fi.csc.avaa.paituli.download.DownloadRequestException;
import fi.csc.avaa.paituli.download.io.FileOperationException;
import fi.csc.avaa.paituli.model.DownloadJob;
import fi.csc.avaa.paituli.model.DownloadRequest;
import io.quarkus.runtime.StartupEvent;

@ExtendWith(MockitoExtension.class)
public class DownloadServiceTest {

    @Spy
    DownloadGenerator downloadGenerator;

    @Mock
    LogService logService;

    @Mock
    ManagedExecutor managedExecutor;

    @InjectMocks
    private DownloadService service;

    private final String filePrefix = "batman_";
    private final String outputPath = "/bar";

    @BeforeEach
    public void init() {
        // Dummy configs so we don't have to boot up testcontainers
        service.filePrefix = filePrefix;
        service.outputPath = outputPath;
        service.keepAliveTimeout = Duration.ofSeconds(15);
    }

    public void interceptAsyncJobProcessing() {
        // Makes any runAsync calls to the executor run synchronously
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            //return CompletableFuture.completedFuture(null);
            return null;
        }).when(managedExecutor).runAsync(any(Runnable.class));
    }

    @Test
    public void createdJobShouldMatchRequestAndConfigFields() {

        // Dummy request
        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;

        // Create job and ensure its contents are as we expect
        DownloadJob job = service.createDownloadJob(request);
        assert(job.request).equals(request);
        assert(job.outputFilename).startsWith(service.filePrefix);
        assert(job.outputFilename).endsWith(request.downloadType.getExtension());
        assert(job.outputFilePath).startsWith(service.outputPath);
    }

    @Test
    public void receivedJobShouldBeLoggedWithItsIdAndOutput() {
        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;
        request.data_id = "some_dataset";

        try (LogCapture log = LogCapture.of(DownloadService.class)) {
            DownloadJob job = service.createDownloadJob(request);

            assertThat(log.lines()).hasSize(1);
            Line line = log.lines().get(0);
            assertThat(line.level()).isEqualTo("INFO");
            assertThat(line.message()).contains("ZIP", "some_dataset", job.ID, job.outputFilePath);
        }
    }

    @Test
    public void jobShouldBeStoredAfterCreation() {

        // Create job with a dummy request
        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;
        DownloadJob job = service.createDownloadJob(request);

        // Check that the job is now stored and its the one we expect
        DownloadJob stored = service.getJob(job.ID);
        assert(stored).equals(job);
    }

    @Test
    public void firstPollShouldDispatchJobToTheGenerator() {
        interceptAsyncJobProcessing();
        Mockito.doNothing().when(downloadGenerator).processJob(any(DownloadJob.class));

        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;

        DownloadJob job = service.createDownloadJob(request);

        // Creating a job alone does no work
        Mockito.verify(downloadGenerator, never()).processJob(any(DownloadJob.class));

        service.pollJob(job.ID);

        // The job the caller gets back must be the one handed to the worker
        Mockito.verify(downloadGenerator).processJob(job);
    }

    @Test
    public void unknownJobIdShouldReturnNull() {
        assertThat(service.getJob("no-such-job")).isNull();
    }

    // Shorthand for a job that is not tied to a real output directory
    private DownloadJob dummyJob() {
        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;
        return new DownloadJob(request, filePrefix, outputPath);
    }

    @Test
    public void processingJobShouldBeCancelled() {
        DownloadJob job = dummyJob();
        job.progress = 0.4;

        assertThat(service.cancel(job, "test")).isTrue();
        assertThat(job.cancelled).isTrue();
    }

    @Test
    public void cancellingCompletedJobShouldNotDestroyItsOutput() {
        DownloadJob job = dummyJob();
        job.progress = 1.0;

        // A cancel arriving after completion must not deny access to a package
        // that is complete and still on disk.
        assertThat(service.cancel(job, "test")).isFalse();
        assertThat(job.cancelled).isFalse();
    }

    @Test
    public void cancellingAlreadyCancelledJobShouldBeANoOp() {
        DownloadJob job = dummyJob();
        job.cancelled = true;

        // A cancelled job never reaches progress 1.0, so it still looks
        // "processing", but there is nothing left to cancel
        assertThat(service.cancel(job, "test")).isFalse();
    }

    @Test
    public void cancellingFailedJobShouldNotMaskTheFailure() {
        DownloadJob job = dummyJob();

        // A failed job never reaches progress 1.0, so it still looks "processing"
        job.error = "something broke";

        // The failure is the more useful thing to report, and cancelling
        // something that already stopped should not change what is reported.
        assertThat(service.cancel(job, "test")).isFalse();
        assertThat(job.cancelled).isFalse();
    }

    @Test
    public void cancellationShouldBeLoggedWithItsReason() {
        DownloadJob job = dummyJob();

        try (LogCapture log = LogCapture.of(DownloadService.class)) {
            service.cancel(job, "requested by client");

            assertThat(log.lines())
                    .extracting(Line::level, Line::message)
                    .containsExactly(tuple("INFO", job + " Cancelled: requested by client"));
        }
    }

    @Test
    public void refusedCancellationShouldNotBeLogged() {
        DownloadJob job = dummyJob();
        job.progress = 1.0;

        try (LogCapture log = LogCapture.of(DownloadService.class)) {
            service.cancel(job, "requested by client");

            assertThat(log.lines()).isEmpty();
        }
    }

    // Shorthand for a job held by the service, so the keep-alive sweep sees it
    private DownloadJob storedJob() {
        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;
        return service.createDownloadJob(request);
    }

    private static long seconds(long seconds) {
        return Duration.ofSeconds(seconds).toNanos();
    }

    @Test
    public void lookingUpJobShouldMarkItAsSeen() {
        DownloadJob job = storedJob();
        job.lastSeen = System.nanoTime() - seconds(60);
        long before = System.nanoTime();

        service.getJob(job.ID);

        assertThat(job.lastSeen).isGreaterThanOrEqualTo(before);
    }

    @Test
    public void jobNotSeenWithinTimeoutShouldBeCancelled() {
        DownloadJob job = storedJob();

        service.cancelAbandonedJobs(job.lastSeen + seconds(16));

        assertThat(job.cancelled).isTrue();
    }

    @Test
    public void abandonedJobShouldBeLoggedWithTheTimeout() {
        DownloadJob job = storedJob();

        try (LogCapture log = LogCapture.of(DownloadService.class)) {
            service.cancelAbandonedJobs(job.lastSeen + seconds(16));

            assertThat(log.lines())
                    .filteredOn(line -> line.level().equals("INFO"))
                    .extracting(Line::message)
                    .containsExactly(job + " Cancelled: client not seen for 15 s");
        }
    }

    @Test
    public void jobSeenWithinTimeoutShouldKeepRunning() {
        DownloadJob job = storedJob();

        service.cancelAbandonedJobs(job.lastSeen + seconds(14));

        assertThat(job.cancelled).isFalse();
    }

    @Test
    public void lookingUpJobShouldKeepItFromBeingCancelled() {
        DownloadJob job = storedJob();
        job.lastSeen = System.nanoTime() - seconds(60);

        // The frontend's status poll, arriving before the sweep
        service.getJob(job.ID);
        service.cancelAbandonedJobs(System.nanoTime());

        assertThat(job.cancelled).isFalse();
    }

    @Test
    public void abandonedJobThatAlreadyStoppedShouldNotBeCancelled() {
        DownloadJob completed = storedJob();
        completed.progress = 1.0;
        DownloadJob failed = storedJob();
        failed.error = "something broke";

        service.cancelAbandonedJobs(System.nanoTime() + seconds(60));

        // The sweep follows the same policy as a cancel from the client
        assertThat(completed.cancelled).isFalse();
        assertThat(failed.cancelled).isFalse();
    }

    @Test
    public void repeatedPollsShouldStartJobOnce() {
        DownloadJob job = storedJob();

        service.pollJob(job.ID);
        service.pollJob(job.ID);
        service.pollJob(job.ID);

        Mockito.verify(managedExecutor, times(1)).runAsync(any(Runnable.class));
    }

    @Test
    public void lookupsOtherThanPollShouldNotStartJob() {
        DownloadJob job = storedJob();

        // Cancel and download requests look the job up with getJob
        service.getJob(job.ID);

        Mockito.verify(managedExecutor, never()).runAsync(any(Runnable.class));
    }

    @Test
    public void jobCancelledBeforeFirstPollShouldNeverStart() {
        DownloadJob job = storedJob();

        service.cancel(job, "test");
        service.pollJob(job.ID);

        Mockito.verify(managedExecutor, never()).runAsync(any(Runnable.class));
    }

    @Test
    public void abandonedJobThatNeverStartedShouldBeForgotten() {
        DownloadJob job = storedJob();

        try (LogCapture log = LogCapture.of(DownloadService.class)) {
            service.cancelAbandonedJobs(job.lastSeen + seconds(16));

            assertThat(service.getJob(job.ID)).isNull();
            assertThat(log.lines())
                    .extracting(Line::level, Line::message)
                    .contains(tuple("DEBUG", job + " Forgotten, as it was cancelled before it started"));
        }
    }

    @Test
    public void abandonedJobThatStartedShouldBeKept() {
        DownloadJob job = storedJob();
        service.pollJob(job.ID);

        service.cancelAbandonedJobs(job.lastSeen + seconds(16));

        // It may have output on disk; how long finished jobs are kept is
        // deferred to FINDINGS #10
        assertThat(job.cancelled).isTrue();
        assertThat(service.getJob(job.ID)).isSameAs(job);
    }

    @Test
    public void jobCancelledWhileWaitingShouldNeverRun() {
        DownloadJob job = storedJob();
        service.pollJob(job.ID);

        ArgumentCaptor<Runnable> queued = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(managedExecutor).runAsync(queued.capture());

        // Cancelled before its turn comes
        service.cancel(job, "test");
        queued.getValue().run();

        Mockito.verify(downloadGenerator, never()).processJob(any(DownloadJob.class));
    }

    // Starts a job and runs the task it hands to the executor, as a worker would
    private void runOnWorker(DownloadJob job) {
        service.pollJob(job.ID);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(managedExecutor).runAsync(task.capture());
        task.getValue().run();
    }

    @Test
    public void errorEscapingTheGeneratorShouldFailTheJob() {
        DownloadJob job = storedJob();
        StackOverflowError error = new StackOverflowError();
        Mockito.doThrow(error).when(downloadGenerator).processJob(job);

        try (LogCapture log = LogCapture.of(DownloadService.class)) {
            runOnWorker(job);

            // Left looking like it is still processing, the job would keep
            // its client polling, and so alive, indefinitely
            assertThat(job.failed()).isTrue();
            assertThat(log.lines())
                    .filteredOn(line -> line.level().equals("ERROR"))
                    .extracting(Line::thrown)
                    .containsExactly(error);
        }
    }

    @Test
    public void completedJobShouldLogItsTimeIncludingTheQueue() {
        DownloadJob job = storedJob();
        Mockito.doNothing().when(downloadGenerator).processJob(job);

        try (LogCapture log = LogCapture.of(DownloadService.class)) {
            runOnWorker(job);

            assertThat(log.lines())
                    .filteredOn(line -> line.level().equals("INFO"))
                    .extracting(Line::message)
                    .hasSize(2)
                    .anyMatch(message -> message.matches(".* Started after \\d+\\.\\d s in queue"))
                    .anyMatch(message -> message.matches(".* Completed in \\d+\\.\\d s, \\d+\\.\\d s of it in queue"));
        }
    }

    @Test
    public void cancelledJobShouldLogThatItStoppedInsteadOfCompleting() {
        DownloadJob job = storedJob();
        // Cancelled while processing
        Mockito.doAnswer(invocation -> {
            job.cancelled = true;
            return null;
        }).when(downloadGenerator).processJob(job);

        try (LogCapture log = LogCapture.of(DownloadService.class)) {
            runOnWorker(job);

            // Why it was cancelled is logged by cancel()
            assertThat(log.lines())
                    .extracting(Line::level, Line::message)
                    .contains(tuple("DEBUG", job + " Stopped after cancellation"));
            assertThat(log.lines())
                    .extracting(Line::message)
                    .noneMatch(message -> message.contains("Completed"));
        }
    }

    @Test
    public void generationFailureShouldBeRecordedOnTheJob() {
        DownloadJob job = storedJob();
        FileOperationException failure = new FileOperationException(new IOException("disk full"));
        Mockito.doThrow(failure).when(downloadGenerator).processJob(job);

        try (LogCapture log = LogCapture.of(DownloadService.class)) {
            runOnWorker(job);

            // The details may describe the server, so only the log gets them
            assertThat(job.error).isEqualTo(DownloadService.SERVER_ERROR);
            assertThat(log.lines())
                    .filteredOn(line -> line.level().equals("ERROR"))
                    .extracting(Line::message, Line::thrown)
                    .containsExactly(tuple(job + " Failed: java.io.IOException: disk full", failure));
        }
    }

    @Test
    public void failureCausedByTheRequestShouldBeAWarningWithoutStackTrace() {
        DownloadJob job = storedJob();
        Mockito.doThrow(new DownloadRequestException("There were no existing files listed in the request"))
                .when(downloadGenerator).processJob(job);

        try (LogCapture log = LogCapture.of(DownloadService.class)) {
            runOnWorker(job);

            assertThat(job.error).isEqualTo("There were no existing files listed in the request");
            assertThat(log.lines())
                    .filteredOn(line -> !line.level().equals("INFO"))
                    .extracting(Line::level, Line::message, Line::thrown)
                    .containsExactly(tuple("WARN", job + " Failed: " + job.error, null));
        }
    }

    @Test
    public void startupShouldLogTheConfig() {
        service.maxConcurrentJobs = 12;

        try (LogCapture log = LogCapture.of(DownloadService.class)) {
            service.onStart(new StartupEvent());

            assertThat(log.lines())
                    .extracting(Line::level, Line::message)
                    .containsExactly(tuple("INFO",
                            "Writing downloads to /bar, at most 12 jobs at once, cancelling jobs unseen for 15 s"));
        }
    }

    @Test
    public void shutdownShouldWarnAboutUnfinishedJobs() {
        DownloadJob running = storedJob();
        service.pollJob(running.ID);
        DownloadJob completed = storedJob();
        service.pollJob(completed.ID);
        completed.progress = 1.0;
        // Never polled, so never started and nothing lost
        storedJob();

        try (LogCapture log = LogCapture.of(DownloadService.class)) {
            service.shutdown();

            assertThat(log.lines())
                    .extracting(Line::level, Line::message)
                    .containsExactly(tuple("WARN", "Unfinished jobs lost in shutdown: 1"));
        }
    }

    @Test
    public void jobInterruptedByShutdownShouldNotBeLoggedAsAFailure() {
        DownloadJob job = storedJob();
        service.pollJob(job.ID);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(managedExecutor).runAsync(task.capture());
        // Interrupting a copy makes it fail with an I/O error
        Mockito.doThrow(new FileOperationException(new IOException("interrupted")))
                .when(downloadGenerator).processJob(job);

        try (LogCapture log = LogCapture.of(DownloadService.class)) {
            service.shutdown();
            task.getValue().run();

            assertThat(log.lines())
                    .extracting(Line::level, Line::message)
                    .contains(tuple("INFO", job + " Interrupted by shutdown"))
                    .noneMatch(line -> line.toList().contains("ERROR"));
        }
    }

    @Test
    public void failureAfterTheJobCompletedShouldOnlyBeLogged() {
        DownloadJob job = storedJob();
        RuntimeException failure = new RuntimeException("statistics write failed");
        Mockito.doAnswer(invocation -> {
            job.progress = 1.0;
            throw failure;
        }).when(downloadGenerator).processJob(job);

        try (LogCapture log = LogCapture.of(DownloadService.class)) {
            runOnWorker(job);

            // The package is complete, so it stays available
            assertThat(job.failed()).isFalse();
            assertThat(log.lines())
                    .filteredOn(line -> line.level().equals("ERROR"))
                    .extracting(Line::thrown)
                    .containsExactly(failure);
        }
    }

    @Test
    public void jobsBeyondTheCapShouldWaitAtZeroProgress() throws InterruptedException {
        // A real executor instead of the mock, capped at two jobs
        service.maxConcurrentJobs = 2;
        service.init();

        // Each job reports some progress, then holds its slot until released
        CountDownLatch running = new CountDownLatch(3);
        CountDownLatch release = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            DownloadJob job = invocation.getArgument(0);
            job.progress = 0.5;
            running.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(downloadGenerator).processJob(any(DownloadJob.class));

        try {
            DownloadJob first = storedJob();
            DownloadJob second = storedJob();
            DownloadJob third = storedJob();
            service.pollJob(first.ID);
            service.pollJob(second.ID);
            service.pollJob(third.ID);

            // Two jobs run, and the third doesn't start while they hold the slots
            assertThat(running.await(500, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(running.getCount()).isEqualTo(1);
            assertThat(third.progress).isZero();

            // Once a slot frees up, the waiting job gets its turn
            release.countDown();
            assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(third.progress).isEqualTo(0.5);
        } finally {
            release.countDown();
            service.shutdown();
        }
    }
}
