package fi.csc.avaa.paituli.download;

import fi.csc.avaa.paituli.constants.DownloadType;

import javax.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class PackageGenerator extends DownloadGeneratorBase {

    public String generate(List<String> filePaths) {
        List<String> absolutePaths = collectAbsolutePaths(filePaths);
        String outputFileName = getOutputFilename(DownloadType.ZIP);
        String outputFilePath = getOutputFilePath(outputFileName);
        fileOperations.packageFiles(absolutePaths, outputFilePath);
        return getDownloadUrl(outputFileName);
    }
}
