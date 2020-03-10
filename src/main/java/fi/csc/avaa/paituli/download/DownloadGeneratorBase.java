package fi.csc.avaa.paituli.download;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.io.FileOperations;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public abstract class DownloadGeneratorBase {

    @Inject
    FileOperations fileOperations;

    @ConfigProperty(name = "paituli.download.inputPath")
    String inputPath;

    @ConfigProperty(name = "paituli.download.outputPath")
    String outputPath;

    @ConfigProperty(name = "paituli.download.outputBaseUrl")
    String outputBaseUrl;

    @ConfigProperty(name = "paituli.download.filePrefix")
    String filePrefix;

    public abstract String generate(String[] filePaths);

    String getOutputFilename(DownloadType type) {
        String randomNumbers = new Random().ints(8, 0, 10)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining());
        return String.format("%s%s.%s", filePrefix, randomNumbers, type.getExtension());
    }

    String getOutputFilePath(String filename) {
        return String.format("%s/%s", outputPath, filename);
    }

    String getDownloadUrl(String outputFilename) {
        return String.format("%s/%s", outputBaseUrl, outputFilename);
    }

    List<String> collectAbsolutePaths(String[] filePaths) {
        List<String> absolutePaths = new ArrayList<>();
        for (String filePath : filePaths) {
            String absolutePath = String.format("%s/%s", inputPath, filePath);
            if (absolutePath.contains("*")) {
                absolutePaths.addAll(findMatchingFiles(absolutePath));
            } else {
                if (fileOperations.fileExists(absolutePath)) {
                    absolutePaths.add(absolutePath);
                } else {
                    System.err.println("Requested file cannot be found from path " + absolutePath);
                }
            }
        }
        if (absolutePaths.isEmpty()) {
            throw new IllegalArgumentException("There were no existing files listed in the filename list");
        }
        return absolutePaths;
    }

    private List<String> findMatchingFiles(String absolutePath) {
        int lastSeparatorIndex = absolutePath.lastIndexOf('/');
        String basePath = absolutePath.substring(0, lastSeparatorIndex);
        String regex = toRegex(absolutePath.substring(lastSeparatorIndex + 1));
        return fileOperations.findFilenamesMatchingRegex(basePath, regex);
    }

    private static String toRegex(String filenameWithWildcard) {
        return filenameWithWildcard
                .replace(".", "\\.")
                .replace("?", ".?")
                .replace("*", ".*");
    }
}
