package fi.csc.avaa.paituli.constants;

public enum DownloadType {

    LIST(Constants.EXTENSION_TXT),
    ZIP(Constants.EXTENSION_ZIP);

    private String extension;

    DownloadType(String extension) {
        this.extension = extension;
    }

    public String getExtension() {
        return extension;
    }
}
