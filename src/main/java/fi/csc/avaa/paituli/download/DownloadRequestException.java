package fi.csc.avaa.paituli.download;

// Thrown when a download can't be made as requested, e.g. none of the
// requested files exist. Nothing is wrong with the service itself, so the
// failure is logged as a warning without a stack trace.
public class DownloadRequestException extends IllegalArgumentException {

    public DownloadRequestException(String message) {
        super(message);
    }
}
