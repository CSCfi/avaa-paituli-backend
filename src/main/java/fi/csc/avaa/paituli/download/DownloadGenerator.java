package fi.csc.avaa.paituli.download;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import fi.csc.avaa.paituli.download.io.FileOperations;
import fi.csc.avaa.paituli.download.io.FileSizeOperations;
import fi.csc.avaa.paituli.download.io.FileSizesException;
import fi.csc.avaa.paituli.download.io.FileOperations.ZipProgress;
import fi.csc.avaa.paituli.service.LogService;
import fi.csc.avaa.paituli.model.DownloadJob;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
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

    // Logs the effective config once at startup, so a deploy shows what the
    // environment actually set
    void onStart(@Observes StartupEvent event) {
        LOG.infof("Reading files from %s, URL lists pointing to %s", inputPath, ftpBaseUrl);
    }

    public void generate(DownloadJob job) {
        switch (job.request.downloadType) {
            case ZIP: generateZip(job); break;
            case LIST: generateUrlList(job); break;
            default:
                throw new IllegalArgumentException(
                    "Unsupported download type " + job.request.downloadType.toString());
        }
    }

    // Generates the job's output. A failure is thrown to the caller, which
    // records it on the job. DownloadService logs when the job starts and how
    // it ends; this class logs the details in between.
    public void processJob(DownloadJob job) {
        generate(job);
        if (!job.cancelled) {
            job.progress = 1.0; // Signals job completion
        }
        // The download itself is done, so a failure here is only logged
        try {
            logService.log(job.request);
        } catch (Exception err) {
            LOG.errorf(err, "%s Could not record download statistics", job);
        }
    }

    private void generateZip(DownloadJob job) {
        List<String> paths = collectAbsolutePaths(job);
        long filesSize = fileSizeOperations.count(paths);
        LOG.debugf("%s Resolved %d requested paths to %d existing paths, %s of the %d GiB limit",
            job, job.request.filePaths.size(), paths.size(), gib(filesSize), MAXSIZE / G);
        if (filesSize > MAXSIZE ) {
            throw new FileSizesException(String.format("The requested files total %s, over the %d GiB limit",
                gib(filesSize), MAXSIZE / G));
        }
        for (ZipProgress zip : fileOperations.zipper(job, paths))
        {
            job.progress = zip.progress(); 
            LOG.debugf("%s Zipped %s", job, zip.added());
        }
    }

    private void generateUrlList(DownloadJob job) {
        List<String> urls = collectFtpUrls(job);
        LOG.debugf("%s Resolved %d requested paths to %d existing paths",
            job, job.request.filePaths.size(), urls.size());
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
                    throw new DownloadRequestException("No files match the requested path " + filePath);
                }
                absolutePaths.addAll(found);
            // Otherwise we simply add the file, if it exists.
            } else {
                if (fileOperations.fileExists(absolutePath)) {
                    absolutePaths.add(absolutePath);
                } else {
                    LOG.warnf("%s Requested file not found, left out of the download: %s", job, absolutePath);
                }
            }
        });
        if (absolutePaths.isEmpty()) {
            throw new DownloadRequestException("There were no existing files listed in the request");
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

    // Formats a size in bytes, e.g. "2.1 GiB"
    private static String gib(long bytes) {
        return String.format(Locale.ROOT, "%.1f GiB", bytes / (double) G);
    }

    private static String toRegex(String filenameWithWildcard) {
        return filenameWithWildcard
                .replace(".", "\\.")
                .replace("?", ".?")
                .replace("*", ".*");
    }

}
