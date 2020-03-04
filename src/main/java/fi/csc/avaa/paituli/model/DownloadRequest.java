package fi.csc.avaa.paituli.model;

import javax.validation.constraints.NotBlank;

public class DownloadRequest {

    @NotBlank
    public String filePaths;
    public String org;
    public String data;
    public String scale;
    public String year;
    public String coordsys;
    public String format;
    @NotBlank
    public String email;
    public String dataId;
    public String downloadType;
}
