package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.entity.LogEvent;
import fi.csc.avaa.paituli.model.DownloadRequest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@Tag("integration")
public class LogServiceTest {

    @Inject
    LogService service;

    @BeforeEach
    @Transactional
    public void deleteAll() {
        LogEvent.deleteAll();
    }

    @Test
    public void logEventShouldBePersisted() {
        final String email = "test@example.com";
        final List<String> filenames = Arrays.asList("test1.zip", "test2.zip");
        final String dataId = "il_sade_1km_1991_tiff_euref";
        final DownloadRequest request = new DownloadRequest();
        request.email = email;
        request.filenames = filenames;
        request.dataId = dataId;
        final String expectedOrganization = "example.com";

        service.log(request);

        List<LogEvent> events = LogEvent.listAll();
        assertThat(events).hasSize(1);
        LogEvent event = events.get(0);
        assertThat(event.timestamp).isNotNull();
        assertThat(event.saltedhash).isNotEqualTo(email);
        assertThat(event.data_id).isEqualTo(dataId);
        assertThat(event.organization).isEqualTo(expectedOrganization);
        assertThat(event.numberOfFiles).isEqualTo(filenames.size() - 1);
    }
}
