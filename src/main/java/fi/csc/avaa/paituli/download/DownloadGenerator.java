package fi.csc.avaa.paituli.download;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.download.io.FileOperations;
import fi.csc.avaa.paituli.model.DownloadRequest;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

@ApplicationScoped
public class DownloadGenerator {

    private static final Logger LOG = Logger.getLogger(DownloadGenerator.class);

    @Inject
    FileOperations fileOperations;

    @ConfigProperty(name = "paituli.download.inputPath")
    String inputPath;

    @ConfigProperty(name = "paituli.download.outputPath")
    String outputPath;

    @ConfigProperty(name = "paituli.download.outputBaseUrl")
    String outputBaseUrl;

    @ConfigProperty(name = "paituli.download.ftpBaseUrl")
    String ftpBaseUrl;

    @ConfigProperty(name = "paituli.download.filePrefix")
    String filePrefix;

    public String generate(DownloadRequest request) {
        return request.downloadType.equals(DownloadType.ZIP)
                ? generatePackage(request.filePaths)
                : generateUrlList(request.filePaths);
    }

    private String generatePackage(List<String> filePaths) {
        List<String> absolutePaths = collectAbsolutePaths(filePaths);
        String outputFileName = getOutputFilename(DownloadType.ZIP);
        String outputFilePath = getOutputFilePath(outputFileName);
        fileOperations.packageFiles(absolutePaths, outputFilePath);
        return getDownloadUrl(outputFileName);
    }

    private String generateUrlList(List<String> filePaths) {
        List<String> ftpUrls = collectFtpUrls(filePaths);
        String outputFileName = getOutputFilename(DownloadType.LIST);
        String outputFilePath = getOutputFilePath(outputFileName);
        fileOperations.writeUrlList(ftpUrls, outputFilePath);
        return getDownloadUrl(outputFileName);
    }

    private String getOutputFilename(DownloadType type) {
        String randomNumbers = new Random().ints(8, 0, 10)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining());
        return String.format("%s%s.%s", filePrefix, randomNumbers, type.getExtension());
    }

    private String getOutputFilePath(String filename) {
        return String.format("%s/%s", outputPath, filename);
    }

    private String getDownloadUrl(String outputFilename) {
        return String.format("%s/%s", outputBaseUrl, outputFilename);
    }

    private List<String> collectAbsolutePaths(List<String> filePaths) {
        List<String> absolutePaths = new ArrayList<>();
        filePaths.forEach(filePath -> {
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
        });
        if (absolutePaths.isEmpty()) {
            throw new IllegalArgumentException("There were no existing files listed in the filename list");
        }
        LOG.info("AbsolutePaths: " + absolutePaths);
        return absolutePaths;
    }

    private List<String> collectFtpUrls(List<String> filePaths) {
        return collectAbsolutePaths(filePaths)
                .stream()
                .sorted()
                .map(absolutePath -> String.format("%s%s", ftpBaseUrl, absolutePath))
                .collect(Collectors.toList());
    }

    private List<String> findMatchingFiles(String absolutePath) {
        int lastSeparatorIndex = absolutePath.lastIndexOf('/');
        String basePath = absolutePath.substring(0, lastSeparatorIndex);
        String regex = toRegex(absolutePath.substring(lastSeparatorIndex + 1));
        LOG.info(fileOperations.findFilenamesMatchingRegex(basePath, regex));
        return fileOperations.findFilenamesMatchingRegex(basePath, regex);
    }

    private static String toRegex(String filenameWithWildcard) {
        return filenameWithWildcard
                .replace(".", "\\.")
                .replace("?", ".?")
                .replace("*", ".*");
    }
}
