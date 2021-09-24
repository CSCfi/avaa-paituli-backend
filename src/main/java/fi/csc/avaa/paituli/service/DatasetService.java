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
        try (Stream<Dataset> stream = Dataset.stream("access", 1)) {
            return stream
                    .map(dataset -> LocalizedDataset.from(dataset, locale))
                    .collect(Collectors.toList());
        }
        catch (Exception e)
24         {
25             System.out.println("Test4");
26             // printStackTrace method
27             // prints line numbers + call stack
28             e.printStackTrace();
29
30             // Prints what exception has been thrown
31             System.out.println(e);
32             return null;
33         }
    }
}
