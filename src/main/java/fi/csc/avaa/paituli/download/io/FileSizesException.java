package fi.csc.avaa.paituli.download.io;

public class FileSizesException extends RuntimeException {
    public FileSizesException(long cause) {
        super(String.valueOf(cause));
    }
}
