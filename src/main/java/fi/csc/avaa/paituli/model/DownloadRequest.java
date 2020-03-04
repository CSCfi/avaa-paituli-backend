package fi.csc.avaa.paituli.model;

import fi.csc.avaa.paituli.constants.DownloadType;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class DownloadRequest {

    @NotBlank
    public String email;
    @NotBlank
    public String filePaths;
    @NotNull
    public DownloadType downloadType;
    public String org;
    public String data;
    public String scale;
    public String year;
    public String coordsys;
    public String format;
    public String dataId;
}
