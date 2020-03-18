package fi.csc.avaa.paituli.model;

import fi.csc.avaa.paituli.constants.DownloadType;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

public class DownloadRequest {

    @NotBlank
    public String email;
    @NotEmpty
    public List<String> filePaths;
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
