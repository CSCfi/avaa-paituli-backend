package fi.csc.avaa.paituli.model;

import fi.csc.avaa.paituli.constants.Constants;
import fi.csc.avaa.paituli.entity.Dataset;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalizedDatasetTest {

    @Test
    public void shouldMapDatasetToLocalizedDatasetFi() {
        verifyDatasetMapping(Constants.LOCALE_FI);
    }

    @Test
    public void shouldMapDatasetToLocalizedDatasetEn() {
        verifyDatasetMapping(Constants.LOCALE_EN);
    }

    private void verifyDatasetMapping(Locale locale) {
        Dataset dataset = new Dataset();
        dataset.data_id = "test_norm_10k_2010_lossless_jp2_euref";
        dataset.org_abbreviation = "TST";
        dataset.org_fin = "Organisaatio suomeksi";
        dataset.org_eng = "Organization in english";
        dataset.name_fin = "Aineisto suomeksi";
        dataset.name_eng = "Dataset in english";
        dataset.scale = "1:10 000";
        dataset.year = "1996-2011";
        dataset.format_fin = "JPEG2000, Häviötön";
        dataset.coord_sys = "ETRS-TM35FIN";
        dataset.map_sheets = 4638;
        dataset.file_size = 70;
        dataset.queries = true;
        dataset.meta = "urn:nbn:fi:test00001000000000000258";
        dataset.access = 5;
        dataset.license_url = "http://www.example.com";
        dataset.format_eng = "JPEG2000, Lossless";
        dataset.funet = "test/test_normal_color/lossless/";

        LocalizedDataset localized = LocalizedDataset.from(dataset, locale);

        assertThat(localized.data_id).isEqualTo(dataset.data_id);
        assertThat(localized.org_abbreviation).isEqualTo(dataset.org_abbreviation);
        assertThat(localized.name_fin).isEqualTo(dataset.name_fin);
        assertThat(localized.scale).isEqualTo(dataset.scale);
        assertThat(localized.year).isEqualTo(dataset.year);
        assertThat(localized.coord_sys).isEqualTo(dataset.coord_sys);
        assertThat(localized.map_sheets).isEqualTo(dataset.map_sheets);
        assertThat(localized.file_size).isEqualTo(dataset.file_size);
        assertThat(localized.queries).isEqualTo(dataset.queries);
        assertThat(localized.meta).isEqualTo(dataset.meta);
        assertThat(localized.access).isEqualTo(dataset.access);
        assertThat(localized.license_url).isEqualTo(dataset.license_url);
        assertThat(localized.funet).isEqualTo(dataset.funet);

        boolean isLocaleFi = locale.equals(Constants.LOCALE_FI);
        assertThat(localized.format).isEqualTo(isLocaleFi ? dataset.format_fin : dataset.format_eng);
        assertThat(localized.name).isEqualTo(isLocaleFi ? dataset.name_fin : dataset.name_eng);
        assertThat(localized.org).isEqualTo(isLocaleFi ? dataset.org_fin : dataset.org_eng);
    }
}
