package fi.csc.avaa.paituli.download.io;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FilenameUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import fi.csc.avaa.paituli.constants.Constants;
import fi.csc.avaa.paituli.model.DownloadJob;
import jakarta.enterprise.context.ApplicationScoped;


@ApplicationScoped
public class FileOperations {

    private static final Logger LOG = Logger.getLogger(FileOperations.class);

    // Cancellation is checked once per chunk, so this bounds how much
    // gets copied after a job is cancelled
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    @ConfigProperty(name = "paituli.download.inputPath")
    String inputPath;

    public List<String> findFilenamesMatchingRegex(String basePath, String regex) {
        try (Stream<Path> paths = Files.list(Paths.get(basePath))) {
            return paths
                    .filter(path -> path.getFileName().toString().matches(regex))
                    .map(path -> path.toFile().getAbsolutePath())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new FileOperationException(e);
        }
    }

    public boolean fileExists(String absolutePath) {
        return Files.exists(Paths.get(absolutePath));
    }

    public record ZipProgress(String added, double progress) {} 

    public Iterable<ZipProgress> zipper(DownloadJob job, List<String> filePaths) {
        return () -> new Iterator<>() {
            // A cancelable iterator for creating zip files and track the zipping progress

            private int numZipped = 0;
            private final Iterator<String> pathsIterator = filePaths.iterator();
            private final ZipOutputStream zout;

            // Initializer
            {
                try {
                    Path zipPath = Files.createFile(Paths.get(job.outputFilePath));
                    LOG.debugf("%s Zipping to %s", job, zipPath);
                    zout = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipPath)));
                } catch (IOException e) {
                    throw new FileOperationException(e);
                }
            }

            @Override
            public boolean hasNext() {
                if (!pathsIterator.hasNext() || job.cancelled) {
                    close();
                    return false;
                }
                return true;
            }

            @Override
            public ZipProgress next() {
                String path = pathsIterator.next();
                copyPathToZip(path, zout, job);
                // A path interrupted by cancellation was not fully zipped
                if (!job.cancelled) numZipped++;
                return new ZipProgress(path, numZipped / (double)filePaths.size());
            }

            private void close()
            {
                try { zout.close(); }
                catch (IOException e) { throw new FileOperationException(e); }
            }
        };
    }

    public void writeUrlList(List<String> urlList, String outputFilepath) {
        try (PrintWriter writer = new PrintWriter(outputFilepath, StandardCharsets.UTF_8)) {
            urlList.forEach(writer::println);
        } catch (IOException e) {
            throw new FileOperationException(e);
        }
    }

    private void copyPathToZip(String absolutePath, ZipOutputStream zout, DownloadJob job) {
        if (Files.isDirectory(Paths.get(absolutePath))) {
            copyDirectoryToZip(absolutePath, zout, job);
        } else {
            copyFileToZip(absolutePath, zout, job);
        }
    }

    private void copyDirectoryToZip(String absolutePath, ZipOutputStream zout, DownloadJob job)  {
        try (Stream<Path> subPaths = Files.list(Paths.get(absolutePath))) {
            subPaths
                    .takeWhile(subPath -> !job.cancelled)
                    .map(Path::toAbsolutePath)
                    .forEach(subPath -> {
                        if (Files.isDirectory(subPath)) {
                            copyDirectoryToZip(subPath.toString(), zout, job);
                        } else {
                            copyFileToZip(subPath.toString(), zout, job);
                        }
                    });
        } catch (IOException e) {
            throw new FileOperationException(e);
        }
    }

    private void copyFileToZip(String absolutePath, ZipOutputStream zout, DownloadJob job) {
        if (absolutePath.endsWith(Constants.EXTENSION_ZIP)) {
            copyZipContentsToZip(absolutePath, zout, job);
        } else {
            copyRegularFileToZip(absolutePath, zout, job);
        }
    }

    private void copyZipContentsToZip(String absolutePath, ZipOutputStream zout, DownloadJob job) {
        zout.setLevel(Deflater.BEST_COMPRESSION);
        String entryPath = FilenameUtils.removeExtension(zipEntryNameFor(absolutePath));
        try (ZipFile source = new ZipFile(absolutePath, StandardCharsets.ISO_8859_1)) {
            Enumeration<? extends ZipEntry> sourceEntries = source.entries();
            while (sourceEntries.hasMoreElements() && !job.cancelled) {
                ZipEntry sourceEntry = sourceEntries.nextElement();
                ZipEntry entry = new ZipEntry(entryPath + "/" + sourceEntry.getName());
                zout.putNextEntry(entry);
                try (InputStream is = source.getInputStream(sourceEntry)) {
                    copyUntilCancelled(is, zout, job);
                }
                zout.closeEntry();
            }
        } catch (IOException e) {
            throw new FileOperationException(e);
        }
    }

    private void copyRegularFileToZip(String absolutePath, ZipOutputStream zout, DownloadJob job) {
        int compressionLevel = absolutePath.endsWith(Constants.EXTENSION_JP2)
                ? Deflater.NO_COMPRESSION
                : Deflater.BEST_COMPRESSION;
        zout.setLevel(compressionLevel);
        try {
            zout.putNextEntry(new ZipEntry(zipEntryNameFor(absolutePath)));
            try (InputStream is = openFile(absolutePath)) {
                copyUntilCancelled(is, zout, job);
            }
            zout.closeEntry();
        } catch (IOException e) {
            throw new FileOperationException(e);
        }
    }

    // Package-private so tests can control how the input is read
    InputStream openFile(String absolutePath) throws IOException {
        return Files.newInputStream(Paths.get(absolutePath));
    }

    // Like InputStream.transferTo, but stops early once the job is cancelled.
    // The entry being written is left truncated, which is fine: the output
    // of a cancelled job is never served.
    private void copyUntilCancelled(InputStream in, ZipOutputStream zout, DownloadJob job)
            throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int read;
        while (!job.cancelled && (read = in.read(buffer)) != -1) {
            zout.write(buffer, 0, read);
        }
    }

    private String zipEntryNameFor(String absolutePath) {
        return absolutePath.substring(inputPath.length());
    }
}
