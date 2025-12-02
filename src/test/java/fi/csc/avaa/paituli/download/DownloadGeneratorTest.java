package fi.csc.avaa.paituli.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.download.io.FileOperationException;
import fi.csc.avaa.paituli.download.io.FileOperations;
import fi.csc.avaa.paituli.model.DownloadJob;
import fi.csc.avaa.paituli.model.DownloadRequest;
import fi.csc.avaa.paituli.service.LogService;

@ExtendWith(MockitoExtension.class)
public class DownloadGeneratorTest {

    @Mock
    private FileOperations fileOperations;

    @Mock
    private LogService logService;

    @Mock
    private ManagedExecutor managedExecutor;

    @Spy
    @InjectMocks
    private DownloadGenerator generator;

    @Captor
    ArgumentCaptor<List<String>> listCaptor;

    @Captor
    ArgumentCaptor<String> stringCaptor;

    private final String inputPath = "/foo/";
    private final String outputPath = "/bar";
    private final String ftpBaseUrl = "http://ftp.example.com";
    private final String filePrefix = "batman_";

    @BeforeEach
    public void init() {
        generator.inputPath = inputPath;
        generator.ftpBaseUrl = ftpBaseUrl;
    }

    // Shorthand method
    DownloadJob processDummyJob(DownloadRequest request) {
        DownloadJob job = new DownloadJob(request, filePrefix, outputPath);
        generator.processJob(job);
        return job;
    }

    @Test
    public void fileShouldBePackagedToOutputFile() {
        final String filePath = "file.zip";
        final List<String> filePaths = Collections.singletonList(filePath);
        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;
        request.filePaths = filePaths;

        Mockito.when(fileOperations.fileExists(absolutePathFor(filePath)))
                .thenReturn(true);

        DownloadJob job = processDummyJob(request);

        assertThat(job.outputFilename)
                .startsWith(filePrefix)
                .endsWith(DownloadType.ZIP.getExtension());

        Mockito.verify(fileOperations)
                .fileExists(absolutePathFor(filePath));

        Mockito.verify(fileOperations)
                .zipper(listCaptor.capture(), stringCaptor.capture());

        assertThat(listCaptor.getValue())
                .hasSize(1)
                .contains(absolutePathFor(filePath));
        assertThat(stringCaptor.getValue())
                .startsWith(outputPath + "/" + filePrefix)
                .endsWith(DownloadType.ZIP.getExtension());
    }


    @Test
    public void fileFtpUrlShouldBeWrittenToOutput() {
        final String filePath = "file.zip";
        final List<String> filePaths = Collections.singletonList(filePath);
        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.LIST;
        request.filePaths = filePaths;

        String absolutePath = absolutePathFor(filePath);
        String ftpUrl = ftpUrlFor(absolutePath);

        Mockito.when(fileOperations.fileExists(absolutePath))
                .thenReturn(true);

        DownloadJob job = processDummyJob(request);

        assertThat(job.outputFilename)
                .startsWith(filePrefix)
                .endsWith(DownloadType.LIST.getExtension());

        Mockito.verify(fileOperations)
                .fileExists(absolutePath);
        Mockito.verify(fileOperations)
                .writeUrlList(listCaptor.capture(), stringCaptor.capture());

        assertThat(listCaptor.getValue())
                .hasSize(1)
                .contains(ftpUrl);
        assertThat(stringCaptor.getValue())
                .startsWith(outputPath + "/" + filePrefix)
                .endsWith(DownloadType.LIST.getExtension());
    }

    @Test
    public void ftpUrlsShouldBeSortedAlphabetically() {
        final String filePathD = "d.zip";
        final String filePathA = "a.zip";
        final String wildcardFilePath = "*.zip";
        final List<String> filePaths = Arrays.asList(filePathD, filePathA, wildcardFilePath);
        final String wildcardFilePathAsRegex = ".*\\.zip";
        final String filePathC = "c.zip";
        final String filePathB = "b.zip";
        final List<String> matchingFiles = Arrays.asList(
                absolutePathFor(filePathC),
                absolutePathFor(filePathB)
        );
        final int lastSeparatorIndex = inputPath.lastIndexOf('/');
        final String basePath = inputPath.substring(0, lastSeparatorIndex);

        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.LIST;
        request.filePaths = filePaths;


        Mockito.when(fileOperations.fileExists(absolutePathFor(filePathD)))
                .thenReturn(true);
        Mockito.when(fileOperations.fileExists(absolutePathFor(filePathA)))
                .thenReturn(true);
        Mockito.when(fileOperations.findFilenamesMatchingRegex(basePath, wildcardFilePathAsRegex))
                .thenReturn(matchingFiles);

        DownloadJob job = processDummyJob(request);

        assertThat(job.outputFilename)
                .startsWith(filePrefix)
                .endsWith(DownloadType.LIST.getExtension());

        Mockito.verify(fileOperations)
                .fileExists(absolutePathFor(filePathD));
        Mockito.verify(fileOperations)
                .fileExists(absolutePathFor(filePathA));
        Mockito.verify(fileOperations)
                .writeUrlList(listCaptor.capture(), stringCaptor.capture());

        assertThat(listCaptor.getValue())
                .hasSize(4)
                .containsExactly(
                        ftpUrlFor(absolutePathFor(filePathA)),
                        ftpUrlFor(absolutePathFor(filePathB)),
                        ftpUrlFor(absolutePathFor(filePathC)),
                        ftpUrlFor(absolutePathFor(filePathD))
                );
        assertThat(stringCaptor.getValue())
                .startsWith(outputPath + "/" + filePrefix)
                .endsWith(DownloadType.LIST.getExtension());
    }

    @Test
    public void nonExistingFileShouldNotCauseExceptionIfAnyOtherFilesAreFound() {
        final String filePath1 = "file.zip";
        final String filePath2 = "doesnotexist.zip";
        final List<String> filePaths = Arrays.asList(filePath1, filePath2);
        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;
        request.filePaths = filePaths;

        Mockito.when(fileOperations.fileExists(absolutePathFor(filePath1)))
                .thenReturn(true);
        Mockito.when(fileOperations.fileExists(absolutePathFor(filePath2)))
                .thenReturn(false);

        DownloadJob job = processDummyJob(request);

        assertThat(job.outputFilename)
                .startsWith(filePrefix)
                .endsWith(DownloadType.ZIP.getExtension());

        Mockito.verify(fileOperations)
                .fileExists(absolutePathFor(filePath1));
        Mockito.verify(fileOperations)
                .fileExists(absolutePathFor(filePath2));
        Mockito.verify(fileOperations)
                .zipper(listCaptor.capture(), stringCaptor.capture());

        assertThat(listCaptor.getValue())
                .hasSize(1)
                .contains(absolutePathFor(filePath1));
        assertThat(stringCaptor.getValue())
                .startsWith(outputPath + "/" + filePrefix)
                .endsWith(DownloadType.ZIP.getExtension());
    }

    @Test
    public void wildcardFilePathsShouldBeExpanded() {
        final String normalFilePath = "normal.zip";
        final String wildcardFilePath = "file*.zip";
        final List<String> filePaths = Arrays.asList(normalFilePath, wildcardFilePath);
        final String wildcardFilePathAsRegex = "file.*\\.zip";
        final List<String> matchingFiles = Arrays.asList(
                absolutePathFor("file1.zip"),
                absolutePathFor("file2.zip")
        );
        final int lastSeparatorIndex = inputPath.lastIndexOf('/');
        final String basePath = inputPath.substring(0, lastSeparatorIndex);

        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;
        request.filePaths = filePaths;

        Mockito.when(fileOperations.fileExists(absolutePathFor(normalFilePath)))
                .thenReturn(true);
        Mockito.when(fileOperations.findFilenamesMatchingRegex(basePath, wildcardFilePathAsRegex))
                .thenReturn(matchingFiles);

        DownloadJob job = processDummyJob(request);

        assertThat(job.outputFilename)
                .startsWith(filePrefix)
                .endsWith(DownloadType.ZIP.getExtension());

        Mockito.verify(fileOperations)
                .fileExists(absolutePathFor(normalFilePath));
        Mockito.verify(fileOperations)
                .findFilenamesMatchingRegex(basePath, wildcardFilePathAsRegex);
        Mockito.verify(fileOperations)
                .zipper(listCaptor.capture(), stringCaptor.capture());

        assertThat(listCaptor.getValue())
                .hasSize(3)
                .contains(
                        absolutePathFor(normalFilePath),
                        matchingFiles.get(0),
                        matchingFiles.get(1)
                );
        assertThat(stringCaptor.getValue())
                .startsWith(outputPath + "/" + filePrefix)
                .endsWith(DownloadType.ZIP.getExtension());
    }

    @Test
    public void shouldThrowExceptionWhenNoFilesAreFound() {
        final String filePath = "file.zip";
        final List<String> filePaths = Collections.singletonList(filePath);
        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;
        request.filePaths = filePaths;

        Mockito.when(fileOperations.fileExists(absolutePathFor(filePath)))
                .thenReturn(false);

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
                processDummyJob(request);
        });
    }

    @Test
    public void exceptionsInFindFilenamesShouldBePropagated() {
        final String wildcardFilePath = "file*.zip";
        final List<String> filePaths = Collections.singletonList(wildcardFilePath);
        final String wildcardFilePathAsRegex = "file.*\\.zip";
        final int lastSeparatorIndex = inputPath.lastIndexOf('/');
        final String basePath = inputPath.substring(0, lastSeparatorIndex);
        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;
        request.filePaths = filePaths;

        Mockito.when(fileOperations.findFilenamesMatchingRegex(basePath, wildcardFilePathAsRegex))
                .thenThrow(new FileOperationException(new IOException()));

        Assertions.assertThrows(FileOperationException.class, () -> {
                processDummyJob(request);
        });
    }

    @Test
    public void exceptionsInWritingOutputShouldBePropagated() {
        final String filePath = "file.zip";
        final List<String> filePaths = Collections.singletonList(filePath);
        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;
        request.filePaths = filePaths;

        Mockito.when(fileOperations.fileExists(absolutePathFor(filePath)))
                .thenReturn(true);
        Mockito.doThrow(new FileOperationException(new IOException()))
                .when(fileOperations).zipper(anyList(), anyString());

        Assertions.assertThrows(FileOperationException.class, () -> {
                processDummyJob(request);
        });
    }

    private String absolutePathFor(String filePath) {
        return String.format("%s%s", inputPath, filePath);
    }

    private String ftpUrlFor(String absolutePath) {
        return String.format("%s%s", ftpBaseUrl, absolutePath);
    }

    @Test
    public void shouldLogAndNotErrorIfGeneratingDoesNotThrow() {

        // Dummy request
        final DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;

        // Start processing but fake generation success
        Mockito.doNothing()
                .when(generator).generate(any(DownloadJob.class));
        DownloadJob job = processDummyJob(request);

        // Ensure we don't have errors and we used the logging service
        assert(job.error).isEmpty();
        Mockito.verify(logService).log(request);
    }

    @Test
    public void jobShouldErrorAndNotLogIfGeneratingThrows() throws InterruptedException {

        // Make generation throw
        Mockito.doThrow(new FileOperationException(new IOException("oops")))
                .when(generator).generate(any());

        // Make mock request and run its job
        final DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;

        // The generator propagates exceptions
        DownloadJob job = new DownloadJob(request, filePrefix, outputPath);
        Assertions.assertThrows(FileOperationException.class, () -> {
                generator.processJob(job);
        });

        // Ensure the throw is handled as expected
        assert(job.error).contains("oops");
        Mockito.verifyNoInteractions(logService);
    }

}
