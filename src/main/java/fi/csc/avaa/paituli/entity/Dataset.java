package fi.csc.avaa.paituli.entity;

import javax.persistence.Entity;
import javax.persistence.Id;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
public class Dataset extends PanacheEntityBase {
    @Id
    public String data_id;
    public String org_abbreviation;
    public String org_fin;
    public String org_eng;
    public String name_fin;
    public String name_eng;
    public String scale;
    public String year;
    public String format_fin;
    public String coord_sys;
    public Integer map_sheets;
    public Integer file_size;
    public String data_url;
    public Boolean queries;
    public Integer data_max_scale;
    public String meta;
    public Integer access;
    public String license_url;
    public String format_eng;
    public String funet;
}
