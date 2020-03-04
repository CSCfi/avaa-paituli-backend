package fi.csc.avaa.paituli.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

@Entity
@Table(name = "loki")
public class LogEvent extends PanacheEntityBase {
    @Id
    @Column(name = "event_id")
    public Integer id;
    @Column(length = 256)
    public String saltedhash;
    @Column(name = "organisaatio", length = 100)
    public String organization;
    @Column(name = "aineisto", length = 256)
    public String dataset;
    @Column(name = "tiedotojenlkm")
    public Integer numberOfFiles;
    @Column(name = "paiva")
    public Date timestamp;
}
