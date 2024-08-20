package fi.csc.avaa.paituli.model;

import fi.csc.avaa.paituli.constants.DownloadType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class DownloadRequest {

    @NotBlank
    public String email;
    @NotEmpty
    public List<String> filePaths;
    @NotEmpty
    public List<String> filenames;
    @NotNull
    public DownloadType downloadType;
    @NotNull
    public String locale;
    public String org;
    public String data;
    public String scale;
    public String year;
    public String coordsys;
    public String format;
    public String data_id;
}
