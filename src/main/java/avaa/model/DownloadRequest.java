package avaa.model;

import javax.validation.constraints.NotBlank;

public class DownloadRequest {

    @NotBlank(message="missing parameter: filePaths")
    public String filePaths;
    public String org;
    public String data;
    public String scale;
    public String year;
    public String coordsys;
    public String format;
    @NotBlank(message="missing parameter: email")
    public String email;
    public String dataId;
    public String downloadType;
}
