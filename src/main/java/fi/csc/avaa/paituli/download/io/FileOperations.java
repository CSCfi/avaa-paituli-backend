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
import java.util.NoSuchElementException;
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
import jakarta.enterprise.context.ApplicationScoped;


@ApplicationScoped
public class FileOperations {

    private static final Logger LOG = Logger.getLogger(FileOperations.class);

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

    public Iterable<ZipProgress> zipper(List<String> filePaths, String outputPath) {
        return () -> new Iterator<>() {
            // An iterator for creating zip files and track the zipping progress

            private int numZipped = 0;
            private final Iterator<String> pathsIterator = filePaths.iterator();
            private final ZipOutputStream zout;

            // Initializer
            {
                try {
                    Path zipPath = Files.createFile(Paths.get(outputPath));
                    LOG.info("Zipping " + zipPath); 
                    zout = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipPath)));
                } catch (IOException e) {
                    throw new FileOperationException(e);
                }
            }

            @Override
            public boolean hasNext() {
                return pathsIterator.hasNext();
            }

            @Override
            public ZipProgress next() {
                if (!hasNext()) throw new NoSuchElementException();

                String path = pathsIterator.next();
                copyPathToZip(path, zout);
                if (!hasNext()) close();
                return new ZipProgress(path, ++numZipped / (double)filePaths.size());
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

    private void copyPathToZip(String absolutePath, ZipOutputStream zout) {
        if (Files.isDirectory(Paths.get(absolutePath))) {
            copyDirectoryToZip(absolutePath, zout);
        } else {
            copyFileToZip(absolutePath, zout);
        }
    }

    private void copyDirectoryToZip(String absolutePath, ZipOutputStream zout)  {
        try (Stream<Path> subPaths = Files.list(Paths.get(absolutePath))) {
            subPaths
                    .map(Path::toAbsolutePath)
                    .forEach(subPath -> {
                        if (Files.isDirectory(subPath)) {
                            copyDirectoryToZip(subPath.toString(), zout);
                        } else {
                            copyFileToZip(subPath.toString(), zout);
                        }
                    });
        } catch (IOException e) {
            throw new FileOperationException(e);
        }
    }

    private void copyFileToZip(String absolutePath, ZipOutputStream zout) {
        if (absolutePath.endsWith(Constants.EXTENSION_ZIP)) {
            copyZipContentsToZip(absolutePath, zout);
        } else {
            copyRegularFileToZip(absolutePath, zout);
        }
    }

    private void copyZipContentsToZip(String absolutePath, ZipOutputStream zout) {
        zout.setLevel(Deflater.BEST_COMPRESSION);
        String entryPath = FilenameUtils.removeExtension(zipEntryNameFor(absolutePath));
        try (ZipFile source = new ZipFile(absolutePath, StandardCharsets.ISO_8859_1)) {
            Enumeration<? extends ZipEntry> sourceEntries = source.entries();
            while (sourceEntries.hasMoreElements()) {
                ZipEntry sourceEntry = sourceEntries.nextElement();
                ZipEntry entry = new ZipEntry(entryPath + "/" + sourceEntry.getName());
                zout.putNextEntry(entry);
                try (InputStream is = source.getInputStream(sourceEntry)) {
                    is.transferTo(zout);
                }
                zout.closeEntry();
            }
        } catch (IOException e) {
            throw new FileOperationException(e);
        }
    }

    private void copyRegularFileToZip(String absolutePath, ZipOutputStream zout) {
        int compressionLevel = absolutePath.endsWith(Constants.EXTENSION_JP2)
                ? Deflater.NO_COMPRESSION
                : Deflater.BEST_COMPRESSION;
        zout.setLevel(compressionLevel);
        try {
            zout.putNextEntry(new ZipEntry(zipEntryNameFor(absolutePath)));
            Files.copy(Paths.get(absolutePath), zout);
            zout.closeEntry();
        } catch (IOException e) {
            throw new FileOperationException(e);
        }
    }

    private String zipEntryNameFor(String absolutePath) {
        return absolutePath.substring(inputPath.length());
    }
}
