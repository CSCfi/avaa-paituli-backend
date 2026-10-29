package fi.csc.avaa.paituli.download.io;

import fi.csc.avaa.paituli.download.DownloadRequestException;

// Thrown when the requested files exceed the size limit
public class FileSizesException extends DownloadRequestException {
    public FileSizesException(String cause) {
        super(cause);
    }
}
