package fi.csc.avaa.paituli.download;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import fi.csc.avaa.paituli.download.io.FileOperations;
import fi.csc.avaa.paituli.download.io.FileSizeOperations;
import fi.csc.avaa.paituli.download.io.FileSizesException;
import fi.csc.avaa.paituli.download.io.FileOperations.ZipProgress;
import fi.csc.avaa.paituli.service.LogService;
import fi.csc.avaa.paituli.model.DownloadJob;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DownloadGenerator {

    private static final Logger LOG = Logger.getLogger(DownloadGenerator.class);
    private static final long K = 1024;
    private static final long G =  K * K * K;
    private static final long MAXSIZE = 15L * G;

    @Inject
    FileOperations fileOperations;

    @Inject
    LogService logService;

    FileSizeOperations fileSizeOperations = new FileSizeOperations();

    @ConfigProperty(name = "paituli.download.inputPath")
    String inputPath;

    @ConfigProperty(name = "paituli.download.ftpBaseUrl")
    String ftpBaseUrl;

    public void generate(DownloadJob job) {
        switch (job.request.downloadType) {
            case ZIP: generateZip(job); break;
            case LIST: generateUrlList(job); break;
            default:
                throw new IllegalArgumentException(
                    "Unsupported download type " + job.request.downloadType.toString());
        }
    }

    public void processJob(DownloadJob job) {
        LOG.infof("%s Starting job processing", job);
        try {
            generate(job);
        }
        catch (Exception err) {
            LOG.error("Could not generate download", err);
            job.error = err.getMessage();
            throw err;
        }
        job.progress = 1.0; // Signals job completion 
        LOG.infof("%s Completed with output %s", job, job.outputFilePath);
        logService.log(job.request);
    }

    private void generateZip(DownloadJob job) {
        List<String> paths = collectAbsolutePaths(job);
        long filesSize = fileSizeOperations.count(paths);
        if (filesSize > MAXSIZE ) {
            throw new FileSizesException(filesSize+" "+job.request.data_id);
        }
        for (ZipProgress zip : fileOperations.zipper(paths, job.outputFilePath))
        {
            job.progress = zip.progress(); 
            LOG.debugf("%s Zipped %s", job, zip.added());
        }
    }

    private void generateUrlList(DownloadJob job) {
        List<String> urls = collectFtpUrls(job);
        fileOperations.writeUrlList(urls, job.outputFilePath);
    }

    private List<String> collectAbsolutePaths(DownloadJob job) {
        List<String> absolutePaths = new ArrayList<>();
        job.request.filePaths.forEach(filePath -> {
            String absolutePath = String.format("%s%s", inputPath, filePath);
            // If we have a wildcard present we do a regex search
            if (absolutePath.contains("*")) {
                List<String> found = findMatchingFiles(absolutePath);
                if (found.isEmpty()) {
                    throw new IllegalArgumentException("Did not find any matches for " + absolutePath);
                }
                absolutePaths.addAll(found);
            // Otherwise we simply add the file, if it exists.
            } else {
                if (fileOperations.fileExists(absolutePath)) {
                    absolutePaths.add(absolutePath);
                } else {
                    LOG.error("Requested file cannot be found from path " + absolutePath);
                }
            }
        });
        if (absolutePaths.isEmpty()) {
            throw new IllegalArgumentException("There were no existing files listed in the request");
        }
        return absolutePaths;
    }

    private List<String> collectFtpUrls(DownloadJob job) {
        return collectAbsolutePaths(job)
                .stream()
                .sorted()
                .map(absolutePath -> String.format("%s%s", ftpBaseUrl, absolutePath))
                .collect(Collectors.toList());
    }

    private List<String> findMatchingFiles(String absolutePath) {
        int lastSeparatorIndex = absolutePath.lastIndexOf('/');
        String basePath = absolutePath.substring(0, lastSeparatorIndex);
        String regex = toRegex(absolutePath.substring(lastSeparatorIndex + 1));
        List<String> found = fileOperations.findFilenamesMatchingRegex(basePath, regex);
        return found;
    }

    private static String toRegex(String filenameWithWildcard) {
        return filenameWithWildcard
                .replace(".", "\\.")
                .replace("?", ".?")
                .replace("*", ".*");
    }

}
