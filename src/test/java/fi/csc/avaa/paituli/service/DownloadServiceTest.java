package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.download.DownloadGenerator;
import fi.csc.avaa.paituli.download.io.FileOperationException;
import fi.csc.avaa.paituli.model.DownloadRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class DownloadServiceTest {

    @Mock
    DownloadGenerator downloadGenerator;

    @Mock
    EmailService emailService;

    @Mock
    LogService logService;

    @InjectMocks
    private DownloadService service;

    @Test
    public void shouldCallPackageGeneratorWhenDownloadTypeIsZip() throws ExecutionException, InterruptedException {
        verifyDownload(DownloadType.ZIP);
    }

    @Test
    public void shouldCallUrlListGeneratorWhenDownloadTypeIsList() throws ExecutionException, InterruptedException {
        verifyDownload(DownloadType.LIST);
    }

    @Test
    public void shouldNotSendEmailOrLogIfDownloadFails() throws InterruptedException {
        final List<String> filePaths = Arrays.asList("test1.zip", "test2.zip");
        final DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.LIST;
        request.filePaths = filePaths;

        Mockito.when(downloadGenerator.generate(request))
                .thenThrow(new FileOperationException(new IOException()));

        try {
            service.generateDownload(request).get();
        } catch (ExecutionException eex) {
            assertThat(eex.getCause())
                    .isInstanceOf(FileOperationException.class);
        }

        Mockito.verifyNoInteractions(emailService);
        Mockito.verifyNoInteractions(logService);
    }

    public void verifyDownload(DownloadType downloadType)
            throws ExecutionException, InterruptedException {
        final String downloadUrl = "https://avaa.tdata.fi/tmp/file.zip";
        final List<String> filePaths = Arrays.asList("test1.zip", "test2.zip");
        final DownloadRequest request = new DownloadRequest();
        request.downloadType = downloadType;
        request.filePaths = filePaths;

        Mockito.when(downloadGenerator.generate(request))
                .thenReturn(downloadUrl);

        CompletableFuture<String> future = service.generateDownload(request);
        String result = future.get();

        assertThat(result).isEqualTo(downloadUrl);

        Mockito.verify(downloadGenerator)
                .generate(request);
        Mockito.verify(emailService)
                .sendEmail(Locale.forLanguageTag("fi"), request, downloadUrl);
        Mockito.verify(logService)
                .log(request);
    }
}
