package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.entity.Dataset;
import fi.csc.avaa.paituli.model.LocalizedDataset;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class DatasetService {

    // Failures, such as the database being unreachable, are left to propagate:
    // the client gets a 500 instead of an empty catalogue, and Quarkus logs
    // the error with an id it also returns to the client
    @Transactional
    public List<LocalizedDataset> getLocalizedDatasets(Locale locale) {
        try (Stream<Dataset> stream = Dataset.stream("access", 1)) {
            return stream
                    .map(dataset -> LocalizedDataset.from(dataset, locale))
                    .collect(Collectors.toList());
        }
    }
}
