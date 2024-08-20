package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.constants.Constants;
import fi.csc.avaa.paituli.entity.Dataset;
import fi.csc.avaa.paituli.model.LocalizedDataset;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@Tag("integration")
public class DatasetServiceTest {

    @Inject
    DatasetService service;

    @BeforeEach
    @Transactional
    public void init() {
        Dataset.deleteAll();
    }

    @Test
    @Transactional
    public void shouldReturnLocalizedDatasetInFinnish() {
        testLocalization(Constants.LOCALE_FI);
    }

    @Test
    @Transactional
    public void shouldReturnLocalizedDatasetInEnglish() {
        testLocalization(Constants.LOCALE_EN);
    }

    @Test
    @Transactional
    public void shouldReturnAllDatasets() {
        final int size = 5;
        for (int i = 0; i < size; i++) {
            Dataset dataset = new Dataset();
            dataset.data_id = String.valueOf(i);
            dataset.persist();
        }

        assertThat(service.getLocalizedDatasets(Constants.LOCALE_FI))
                .hasSize(size);
    }

    private void testLocalization(Locale locale) {
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
        dataset.data_url = "paituli:test_2007_100k";
        dataset.queries = true;
        dataset.data_max_scale = 6000000;
        dataset.meta = "urn:nbn:fi:test00001000000000000258";
        dataset.access = 5;
        dataset.license_url = "http://www.example.com";
        dataset.format_eng = "JPEG2000, Lossless";
        dataset.funet = "test/test_normal_color/lossless/";
        dataset.persist();

        LocalizedDataset expected = LocalizedDataset.from(dataset, locale);

        List<LocalizedDataset> datasets = service.getLocalizedDatasets(locale);

        assertThat(datasets)
                .hasSize(1);
        LocalizedDataset actual = datasets.get(0);
        assertThat(actual)
                .isEqualToComparingFieldByField(expected);
    }
}
