package fi.csc.avaa.paituli.model;

import java.util.Locale;

import fi.csc.avaa.paituli.constants.Constants;
import fi.csc.avaa.paituli.entity.Dataset;

public class LocalizedDataset {

    public String data_id;
    public String name;
    public String name_fin;
    public String org;
    public String org_abbreviation;
    public String format;
    public String scale;
    public String year;
    public String coord_sys;
    public Integer map_sheets;
    public Integer file_size;
    public String data_url;
    public Boolean queries;
    public String meta;
    public Integer access;
    public String license_url;
    public String funet;
    public Integer data_max_scale;

    public static LocalizedDataset from(Dataset dataset, Locale locale) {
        boolean isLocaleFi = locale.equals(Constants.LOCALE_FI);
        LocalizedDataset localized = new LocalizedDataset();
        localized.data_id = dataset.data_id;
        localized.name = isLocaleFi
                ? dataset.name_fin
                : dataset.name_eng;
        localized.name_fin = dataset.name_fin;
        localized.org = isLocaleFi
                ? dataset.org_fin
                : dataset.org_eng;
        localized.org_abbreviation = dataset.org_abbreviation;
        localized.format = isLocaleFi
                ? dataset.format_fin
                : dataset.format_eng;
        localized.scale = dataset.scale;
        localized.year = dataset.year;
        localized.coord_sys = dataset.coord_sys;
        localized.map_sheets = dataset.map_sheets;
        localized.file_size = dataset.file_size;
        localized.data_url = dataset.data_url;
        localized.queries = dataset.queries;
        localized.meta = dataset.meta;
        localized.access = dataset.access;
        localized.license_url = dataset.license_url;
        localized.funet = dataset.funet;
        localized.data_max_scale = dataset.data_max_scale;
        return localized;
    }
}
