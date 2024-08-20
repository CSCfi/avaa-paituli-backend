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

    @Transactional
    public List<LocalizedDataset> getLocalizedDatasets(Locale locale) {
        try (Stream<Dataset> stream = Dataset.stream("access", 1)) {
            return stream
                    .map(dataset -> LocalizedDataset.from(dataset, locale))
                    .collect(Collectors.toList());
        }
        catch (Exception e)         
        {
             System.out.println("Test4");
             // printStackTrace method
             // prints line numbers + call stack
             e.printStackTrace();

             // Prints what exception has been thrown
             System.out.println(e);
             return null;
         }
    }
}
