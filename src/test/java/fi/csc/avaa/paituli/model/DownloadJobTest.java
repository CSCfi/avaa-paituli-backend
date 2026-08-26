package fi.csc.avaa.paituli.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import fi.csc.avaa.paituli.constants.DownloadType;

public class DownloadJobTest {

    private final String filePrefix = "batman_";
    private final String outputPath = "/bar";

    private DownloadJob jobOfType(DownloadType type) {
        DownloadRequest request = new DownloadRequest();
        request.downloadType = type;
        return new DownloadJob(request, filePrefix, outputPath);
    }

    @Test
    public void newJobShouldStartUnfinishedAndClean() {
        DownloadJob job = jobOfType(DownloadType.ZIP);

        assertThat(job.progress).isEqualTo(0.0);
        assertThat(job.cancelled).isFalse();
        assertThat(job.processing()).isTrue();
        assertThat(job.failed()).isFalse();
    }

    @Test
    public void processingShouldEndOnlyAtFullProgress() {
        DownloadJob job = jobOfType(DownloadType.ZIP);

        job.progress = 0.999;
        assertThat(job.processing()).isTrue();

        job.progress = 1.0;
        assertThat(job.processing()).isFalse();
    }

    @Test
    public void failedShouldTreatNullMessageAsFailure() {
        DownloadJob job = jobOfType(DownloadType.ZIP);

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
        DownloadJob zip = jobOfType(DownloadType.ZIP);
        DownloadJob list = jobOfType(DownloadType.LIST);

        assertThat(zip.outputFilename).matches("batman_\\d{8}\\.zip");
        assertThat(list.outputFilename).matches("batman_\\d{8}\\.txt");
        assertThat(zip.outputFilePath).isEqualTo(outputPath + "/" + zip.outputFilename);
    }

    @Test
    public void jobsShouldHaveDistinctIdsAndFilenames() {
        DownloadJob first = jobOfType(DownloadType.ZIP);
        DownloadJob second = jobOfType(DownloadType.ZIP);

        assertThat(first.ID).isNotEqualTo(second.ID);
        assertThat(first.outputFilename).isNotEqualTo(second.outputFilename);
    }
}
