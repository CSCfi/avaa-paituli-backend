package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.entity.Dataset;
import fi.csc.avaa.paituli.model.LocalizedDataset;

import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class DatasetService {

    @Transactional
    public List<LocalizedDataset> getLocalizedDatasets(Locale locale) {
        try (Stream<Dataset> stream = Dataset.streamAll()) {
            return stream
                    .filter(dataset -> dataset.getAccess() != 5)
                    .map(dataset -> LocalizedDataset.from(dataset, locale))
                    .collect(Collectors.toList());
        }
    }
}
