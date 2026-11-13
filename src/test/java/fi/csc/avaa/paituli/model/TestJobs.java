package fi.csc.avaa.paituli.model;

import fi.csc.avaa.paituli.constants.DownloadType;

// Shorthands for the requests and jobs that tests build
public final class TestJobs {

    public static final String FILE_PREFIX = "batman_";
    public static final String OUTPUT_PATH = "/bar";

    private TestJobs() {}

    public static DownloadRequest request(DownloadType type) {
        DownloadRequest request = new DownloadRequest();
        request.downloadType = type;
        return request;
    }

    // A job whose output goes under outputPath
    public static DownloadJob job(DownloadType type, String outputPath) {
        return new DownloadJob(request(type), FILE_PREFIX, outputPath);
    }

    // A job that is not tied to a real output directory
    public static DownloadJob job(DownloadType type) {
        return job(type, OUTPUT_PATH);
    }
}
