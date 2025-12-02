package fi.csc.avaa.paituli.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.download.DownloadGenerator;
import fi.csc.avaa.paituli.model.DownloadJob;
import fi.csc.avaa.paituli.model.DownloadRequest;

@ExtendWith(MockitoExtension.class)
public class DownloadServiceTest {

    @Spy
    DownloadGenerator downloadGenerator;

    @Mock
    LogService logService;

    @Mock
    ManagedExecutor managedExecutor;

    @InjectMocks
    private DownloadService service;

    private final String filePrefix = "batman_";
    private final String outputPath = "/bar";

    @BeforeEach
    public void init() {
        // Dummy configs so we don't have to boot up testcontainers
        service.filePrefix = filePrefix;
        service.outputPath = outputPath;
    }

    public void interceptAsyncJobProcessing() {
        // Makes any runAsync calls to the executor run synchronously
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            //return CompletableFuture.completedFuture(null);
            return null;
        }).when(managedExecutor).runAsync(any(Runnable.class));
    }

    @Test
    public void createdJobShouldMatchRequestAndConfigFields() {

        // Dummy request
        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;

        // Create job and ensure its contents are as we expect
        DownloadJob job = service.createDownloadJob(request);
        assert(job.request).equals(request);
        assert(job.outputFilename).startsWith(service.filePrefix);
        assert(job.outputFilename).endsWith(request.downloadType.getExtension());
        assert(job.outputFilePath).startsWith(service.outputPath);
    }

    @Test
    public void jobShouldBeStoredAfterCreation() {

        // Create job with a dummy request
        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;
        DownloadJob job = service.createDownloadJob(request);

        // Check that the job is now stored and its the one we expect
        DownloadJob stored = service.getJob(job.ID);
        assert(stored).equals(job);
    }


    /*
    @Test
    public void shouldCallPackageGeneratorWhenDownloadTypeIsZip() throws ExecutionException, InterruptedException {
        //verifyDownload(DownloadType.ZIP);
    }

    @Test
    public void shouldCallUrlListGeneratorWhenDownloadTypeIsList() throws ExecutionException, InterruptedException {
        //verifyDownload(DownloadType.LIST);
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
        Mockito.verify(logService)
                .log(request);
    }
    */
}
