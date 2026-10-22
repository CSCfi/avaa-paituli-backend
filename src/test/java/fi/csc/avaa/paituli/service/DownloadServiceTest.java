package fi.csc.avaa.paituli.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.Duration;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.download.DownloadGenerator;
import fi.csc.avaa.paituli.model.DownloadJob;
import fi.csc.avaa.paituli.model.DownloadRequest;

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

        assertThat(service.cancel(job)).isTrue();
        assertThat(job.cancelled).isTrue();
    }

    @Test
    public void cancellingCompletedJobShouldNotDestroyItsOutput() {
        DownloadJob job = dummyJob();
        job.progress = 1.0;

        // A cancel arriving after completion must not deny access to a package
        // that is complete and still on disk.
        assertThat(service.cancel(job)).isFalse();
        assertThat(job.cancelled).isFalse();
    }

    @Test
    public void cancellingAlreadyCancelledJobShouldBeANoOp() {
        DownloadJob job = dummyJob();
        job.cancelled = true;

        // A cancelled job never reaches progress 1.0, so it still looks
        // "processing", but there is nothing left to cancel
        assertThat(service.cancel(job)).isFalse();
    }

    @Test
    public void cancellingFailedJobShouldNotMaskTheFailure() {
        DownloadJob job = dummyJob();

        // A failed job never reaches progress 1.0, so it still looks "processing"
        job.error = "something broke";

        // The failure is the more useful thing to report, and cancelling
        // something that already stopped should not change what is reported.
        assertThat(service.cancel(job)).isFalse();
        assertThat(job.cancelled).isFalse();
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

        service.cancel(job);
        service.pollJob(job.ID);

        Mockito.verify(managedExecutor, never()).runAsync(any(Runnable.class));
    }

    @Test
    public void abandonedJobThatNeverStartedShouldBeForgotten() {
        DownloadJob job = storedJob();

        service.cancelAbandonedJobs(job.lastSeen + seconds(16));

        assertThat(service.getJob(job.ID)).isNull();
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
}
