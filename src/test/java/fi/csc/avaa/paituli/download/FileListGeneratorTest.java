package fi.csc.avaa.paituli.download;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.download.io.FileOperationException;
import fi.csc.avaa.paituli.download.io.FileOperations;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
public class FileListGeneratorTest {

    @Mock
    private FileOperations fileOperations;

    @InjectMocks
    private FileListGenerator generator;

    @Captor
    ArgumentCaptor<List<String>> listCaptor;

    @Captor
    ArgumentCaptor<String> stringCaptor;

    private final String inputPath = "/foo";
    private final String outputPath = "/bar";
    private final String outputBaseUrl = "http://www.example.com";
    private final String ftpBaseUrl = "http://ftp.example.com";
    private final String filePrefix = "batman_";
    private final String downloadUrlPrefix = String.format("%s/%s", outputBaseUrl, filePrefix);

    @BeforeEach
    public void init() {
        generator.inputPath = inputPath;
        generator.outputPath = outputPath;
        generator.outputBaseUrl = outputBaseUrl;
        generator.ftpBaseUrl = ftpBaseUrl;
        generator.filePrefix = filePrefix;
    }

    @Test
    public void fileFtpUrlShouldBeWrittenToOutput() {
        final String filePath = "file.zip";
        final List<String> filePaths = Collections.singletonList(filePath);

        String absolutePath = absolutePathFor(filePath);
        String ftpUrl = ftpUrlFor(absolutePath);

        Mockito.when(fileOperations.fileExists(absolutePath))
                .thenReturn(true);

        String downloadUrl = generator.generate(filePaths);

        assertThat(downloadUrl)
                .startsWith(downloadUrlPrefix)
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
    public void nonExistingFileShouldNotCauseException() {
        final String filePath1 = "file.zip";
        final String filePath2 = "doesnotexist.zip";
        final List<String> filePaths = Arrays.asList(filePath1, filePath2);

        Mockito.when(fileOperations.fileExists(absolutePathFor(filePath1)))
                .thenReturn(true);
        Mockito.when(fileOperations.fileExists(absolutePathFor(filePath2)))
                .thenReturn(false);

        String downloadUrl = generator.generate(filePaths);

        assertThat(downloadUrl)
                .startsWith(downloadUrlPrefix)
                .endsWith(DownloadType.LIST.getExtension());

        Mockito.verify(fileOperations)
                .fileExists(absolutePathFor(filePath1));
        Mockito.verify(fileOperations)
                .fileExists(absolutePathFor(filePath2));
        Mockito.verify(fileOperations)
                .writeUrlList(listCaptor.capture(), stringCaptor.capture());

        assertThat(listCaptor.getValue())
                .hasSize(1)
                .contains(ftpUrlFor(absolutePathFor(filePath1)));
        assertThat(stringCaptor.getValue())
                .startsWith(outputPath + "/" + filePrefix)
                .endsWith(DownloadType.LIST.getExtension());
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

        Mockito.when(fileOperations.fileExists(absolutePathFor(normalFilePath)))
                .thenReturn(true);
        Mockito.when(fileOperations.findFilenamesMatchingRegex(inputPath, wildcardFilePathAsRegex))
                .thenReturn(matchingFiles);

        String downloadUrl = generator.generate(filePaths);

        assertThat(downloadUrl)
                .startsWith(downloadUrlPrefix)
                .endsWith(DownloadType.LIST.getExtension());

        Mockito.verify(fileOperations)
                .fileExists(absolutePathFor(normalFilePath));
        Mockito.verify(fileOperations)
                .findFilenamesMatchingRegex(inputPath, wildcardFilePathAsRegex);
        Mockito.verify(fileOperations)
                .writeUrlList(listCaptor.capture(), stringCaptor.capture());

        assertThat(listCaptor.getValue())
                .hasSize(3)
                .contains(
                        ftpUrlFor(absolutePathFor(normalFilePath)),
                        ftpUrlFor(matchingFiles.get(0)),
                        ftpUrlFor(matchingFiles.get(1)));
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

        Mockito.when(fileOperations.fileExists(absolutePathFor(filePathD)))
                .thenReturn(true);
        Mockito.when(fileOperations.fileExists(absolutePathFor(filePathA)))
                .thenReturn(true);
        Mockito.when(fileOperations.findFilenamesMatchingRegex(inputPath, wildcardFilePathAsRegex))
                .thenReturn(matchingFiles);

        String downloadUrl = generator.generate(filePaths);

        assertThat(downloadUrl)
                .startsWith(downloadUrlPrefix)
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
    public void shouldThrowExceptionWhenNoFilesAreFound() {
        final String filePath = "file.zip";
        final List<String> filePaths = Collections.singletonList(filePath);

        Mockito.when(fileOperations.fileExists(absolutePathFor(filePath)))
                .thenReturn(false);

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            generator.generate(filePaths);
        });
    }

    @Test
    public void exceptionsInFindFilenamesShouldBePropagated() {
        final String wildcardFilePath = "file*.zip";
        final List<String> filePaths = Collections.singletonList(wildcardFilePath);
        final String wildcardFilePathAsRegex = "file.*\\.zip";

        Mockito.when(fileOperations.findFilenamesMatchingRegex(inputPath, wildcardFilePathAsRegex))
                .thenThrow(new FileOperationException(new IOException()));

        Assertions.assertThrows(FileOperationException.class, () -> {
            generator.generate(filePaths);
        });
    }

    @Test
    public void exceptionsInWritingOutputShouldBePropagated() {
        final String filePath = "file.zip";
        final List<String> filePaths = Collections.singletonList(filePath);

        Mockito.when(fileOperations.fileExists(absolutePathFor(filePath)))
                .thenReturn(true);
        Mockito.doThrow(new FileOperationException(new IOException()))
                .when(fileOperations).writeUrlList(anyList(), anyString());

        Assertions.assertThrows(FileOperationException.class, () -> {
            generator.generate(filePaths);
        });
    }

    private String absolutePathFor(String filePath) {
        return String.format("%s/%s", inputPath, filePath);
    }

    private String ftpUrlFor(String absolutePath) {
        return String.format("%s%s", ftpBaseUrl, absolutePath);
    }
}
