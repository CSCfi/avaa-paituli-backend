package fi.csc.avaa.paituli.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import fi.csc.avaa.paituli.constants.DownloadType;

public class DownloadJobTest {

    @Test
    public void newJobShouldStartUnfinishedAndClean() {
        DownloadJob job = TestJobs.job(DownloadType.ZIP);

        assertThat(job.progress).isEqualTo(0.0);
        assertThat(job.cancelled).isFalse();
        assertThat(job.processing()).isTrue();
        assertThat(job.failed()).isFalse();
    }

    @Test
    public void newJobShouldCountAsJustSeen() {
        long before = System.nanoTime();
        DownloadJob job = TestJobs.job(DownloadType.ZIP);

        // Otherwise the keep-alive sweep could cancel a job before the
        // client has had a chance to poll it even once
        assertThat(job.lastSeen).isGreaterThanOrEqualTo(before);
    }

    @Test
    public void processingShouldEndOnlyAtFullProgress() {
        DownloadJob job = TestJobs.job(DownloadType.ZIP);

        job.progress = 0.999;
        assertThat(job.processing()).isTrue();

        job.progress = 1.0;
        assertThat(job.processing()).isFalse();
    }

    @Test
    public void failedShouldTreatNullMessageAsFailure() {
        DownloadJob job = TestJobs.job(DownloadType.ZIP);

        // Empty string is the "no error" sentinel
        assertThat(job.failed()).isFalse();

        job.error = "boom";
        assertThat(job.failed()).isTrue();

        // Null means an exception that carried no message, not "no error"
        job.error = null;
        assertThat(job.failed()).isTrue();
    }

    @Test
    public void outputFilenameShouldUsePrefixAndTypeExtension() {
        DownloadJob zip = TestJobs.job(DownloadType.ZIP);
        DownloadJob list = TestJobs.job(DownloadType.LIST);

        assertThat(zip.outputFilename).matches(TestJobs.FILE_PREFIX + "\\d{8}\\.zip");
        assertThat(list.outputFilename).matches(TestJobs.FILE_PREFIX + "\\d{8}\\.txt");
        assertThat(zip.outputFilePath).isEqualTo(TestJobs.OUTPUT_PATH + "/" + zip.outputFilename);
    }

    @Test
    public void jobsShouldHaveDistinctIdsAndFilenames() {
        DownloadJob first = TestJobs.job(DownloadType.ZIP);
        DownloadJob second = TestJobs.job(DownloadType.ZIP);

        assertThat(first.ID).isNotEqualTo(second.ID);
        assertThat(first.outputFilename).isNotEqualTo(second.outputFilename);
    }
}
