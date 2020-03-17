package fi.csc.avaa.paituli.download;

import fi.csc.avaa.paituli.constants.DownloadType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class UrlListGenerator extends DownloadGeneratorBase {

    @ConfigProperty(name = "paituli.download.ftpBaseUrl")
    String ftpBaseUrl;

    public String generate(List<String> filePaths) {
        List<String> ftpUrls = collectFtpUrls(filePaths);
        String outputFileName = getOutputFilename(DownloadType.LIST);
        String outputFilePath = getOutputFilePath(outputFileName);
        fileOperations.writeUrlList(ftpUrls, outputFilePath);
        return getDownloadUrl(outputFileName);
    }

    private List<String> collectFtpUrls(List<String> filePaths) {
        return collectAbsolutePaths(filePaths)
                .stream()
                .sorted()
                .map(absolutePath -> String.format("%s%s", ftpBaseUrl, absolutePath))
                .collect(Collectors.toList());
    }
}
