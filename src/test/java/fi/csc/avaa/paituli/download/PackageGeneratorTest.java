package fi.csc.avaa.paituli.download;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.io.FileOperationException;
import fi.csc.avaa.paituli.io.FileOperations;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
public class PackageGeneratorTest {

    @Mock
    private FileOperations fileOperations;

    @InjectMocks
    private PackageGenerator generator;

    @Captor
    ArgumentCaptor<List<String>> listCaptor;

    @Captor
    ArgumentCaptor<String> stringCaptor;

    private final String inputPath = "/foo";
    private final String outputPath = "/bar";
    private final String outputBaseUrl = "http://www.example.com";
    private final String filePrefix = "batman_";
    private final String downloadUrlPrefix = String.format("%s/%s", outputBaseUrl, filePrefix);

    @BeforeEach
    public void init() {
        generator.inputPath = inputPath;
        generator.outputPath = outputPath;
        generator.outputBaseUrl = outputBaseUrl;
        generator.filePrefix = filePrefix;
    }

    @Test
    public void fileShouldBePackagedToOutputFile() {
        final String filePath = "file.zip";
        final String[] filePaths = {filePath};

        Mockito.when(fileOperations.fileExists(absolutePathFor(filePath)))
                .thenReturn(true);

        String downloadUrl = generator.generate(filePaths);

        assertThat(downloadUrl)
                .startsWith(downloadUrlPrefix)
                .endsWith(DownloadType.ZIP.getExtension());

        Mockito.verify(fileOperations)
                .fileExists(absolutePathFor(filePath));
        Mockito.verify(fileOperations)
                .packageFiles(listCaptor.capture(), stringCaptor.capture());

        assertThat(listCaptor.getValue())
                .hasSize(1)
                .contains(absolutePathFor(filePath));
        assertThat(stringCaptor.getValue())
                .startsWith(outputPath + "/" + filePrefix)
                .endsWith(DownloadType.ZIP.getExtension());
    }

    @Test
    public void nonExistingFileShouldNotCauseException() {
        final String filePath1 = "file.zip";
        final String filePath2 = "doesnotexist.zip";
        final String[] filePaths = { filePath1, filePath2 };

        Mockito.when(fileOperations.fileExists(absolutePathFor(filePath1)))
                .thenReturn(true);
        Mockito.when(fileOperations.fileExists(absolutePathFor(filePath2)))
                .thenReturn(false);

        String downloadUrl = generator.generate(filePaths);

        assertThat(downloadUrl)
                .startsWith(downloadUrlPrefix)
                .endsWith(DownloadType.ZIP.getExtension());

        Mockito.verify(fileOperations)
                .fileExists(absolutePathFor(filePath1));
        Mockito.verify(fileOperations)
                .fileExists(absolutePathFor(filePath2));
        Mockito.verify(fileOperations)
                .packageFiles(listCaptor.capture(), stringCaptor.capture());

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
        final String[] filePaths = { normalFilePath, wildcardFilePath };
        final String wildcardFilePathAsRegex = "file.*\\.zip";
        final String[] matchingFiles = {
                absolutePathFor("file1.zip"),
                absolutePathFor("file2.zip")
        };

        Mockito.when(fileOperations.fileExists(absolutePathFor(normalFilePath)))
                .thenReturn(true);
        Mockito.when(fileOperations.findFilenamesMatchingRegex(inputPath, wildcardFilePathAsRegex))
                .thenReturn(Arrays.asList(matchingFiles));

        String downloadUrl = generator.generate(filePaths);

        assertThat(downloadUrl)
                .startsWith(downloadUrlPrefix)
                .endsWith(DownloadType.ZIP.getExtension());

        Mockito.verify(fileOperations)
                .fileExists(absolutePathFor(normalFilePath));
        Mockito.verify(fileOperations)
                .findFilenamesMatchingRegex(inputPath, wildcardFilePathAsRegex);
        Mockito.verify(fileOperations)
                .packageFiles(listCaptor.capture(), stringCaptor.capture());

        assertThat(listCaptor.getValue())
                .hasSize(3)
                .contains(
                        absolutePathFor(normalFilePath),
                        matchingFiles[0],
                        matchingFiles[1]
                );
        assertThat(stringCaptor.getValue())
                .startsWith(outputPath + "/" + filePrefix)
                .endsWith(DownloadType.ZIP.getExtension());
    }

    @Test
    public void shouldThrowExceptionWhenNoFilesAreFound() {
        final String filePath = "file.zip";
        final String[] filePaths = {filePath};

        Mockito.when(fileOperations.fileExists(absolutePathFor(filePath)))
                .thenReturn(false);

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            generator.generate(filePaths);
        });
    }

    @Test
    public void exceptionsInFindFilenamesShouldBePropagated() {
        final String wildcardFilePath = "file*.zip";
        final String[] filePaths = { wildcardFilePath };
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
        final String[] filePaths = {filePath};

        Mockito.when(fileOperations.fileExists(absolutePathFor(filePath)))
                .thenReturn(true);
        Mockito.doThrow(new FileOperationException(new IOException()))
                .when(fileOperations).packageFiles(anyList(), anyString());

        Assertions.assertThrows(FileOperationException.class, () -> {
            generator.generate(filePaths);
        });
    }

    private String absolutePathFor(String filePath) {
        return String.format("%s/%s", inputPath, filePath);
    }
}
