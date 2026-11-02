package fi.csc.avaa.paituli.download.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.download.io.FileOperations.ZipProgress;
import fi.csc.avaa.paituli.model.DownloadJob;
import fi.csc.avaa.paituli.model.DownloadRequest;

public class FileOperationsTest {

    // Larger than the copy buffer, so copying it takes several chunks
    private static final int LARGE_FILE_SIZE = 3 * 64 * 1024 + 123;

    @TempDir
    Path tempDir;

    private Path inputDir;
    private FileOperations fileOperations;
    private DownloadJob job;

    @BeforeEach
    public void init() throws IOException {
        inputDir = Files.createDirectory(tempDir.resolve("in"));
        Path outputDir = Files.createDirectory(tempDir.resolve("out"));

        fileOperations = new FileOperations();
        fileOperations.inputPath = inputDir + "/";

        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;
        job = new DownloadJob(request, "test_", outputDir.toString());
    }

    private Path createFile(String relativePath, byte[] content) throws IOException {
        Path path = inputDir.resolve(relativePath);
        Files.createDirectories(path.getParent());
        return Files.write(path, content);
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new Random(42).nextBytes(bytes);
        return bytes;
    }

    // Reads the job's output archive into entry name -> content
    private Map<String, byte[]> readOutput() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(job.outputFilePath)) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                entries.put(entry.getName(), zip.getInputStream(entry).readAllBytes());
            }
        }
        return entries;
    }

    private void zipAll(List<String> paths) {
        for (ZipProgress zip : fileOperations.zipper(job, paths)) { }
    }

    @Test
    public void regularFileShouldBeZippedIntact() throws IOException {
        byte[] content = randomBytes(LARGE_FILE_SIZE);
        Path file = createFile("data/large.bin", content);

        zipAll(List.of(file.toString()));

        Map<String, byte[]> entries = readOutput();
        assertThat(entries).containsOnlyKeys("data/large.bin");
        assertThat(entries.get("data/large.bin")).isEqualTo(content);
    }

    @Test
    public void directoryShouldBeZippedRecursively() throws IOException {
        createFile("dir/a.txt", "a".getBytes());
        createFile("dir/sub/b.txt", "b".getBytes());

        zipAll(List.of(inputDir.resolve("dir").toString()));

        Map<String, byte[]> entries = readOutput();
        assertThat(entries).containsOnlyKeys("dir/a.txt", "dir/sub/b.txt");
        assertThat(entries.get("dir/sub/b.txt")).isEqualTo("b".getBytes());
    }

    @Test
    public void nestedZipShouldBeRepackedEntryByEntry() throws IOException {
        Path nested = createNestedZip("maps.zip", "x.txt", "y.txt");

        zipAll(List.of(nested.toString()));

        Map<String, byte[]> entries = readOutput();
        assertThat(entries).containsOnlyKeys("maps/x.txt", "maps/y.txt");
        assertThat(entries.get("maps/x.txt")).isEqualTo("x.txt".getBytes());
    }

    @Test
    public void cancelShouldStopBetweenPaths() throws IOException {
        Path first = createFile("first.txt", "1".getBytes());
        Path second = createFile("second.txt", "2".getBytes());

        Iterator<ZipProgress> zipper =
            fileOperations.zipper(job, List.of(first.toString(), second.toString())).iterator();
        zipper.next();
        job.cancelled = true;

        assertThat(zipper.hasNext()).isFalse();
        assertThat(readOutput()).containsOnlyKeys("first.txt");
    }

    @Test
    public void cancelShouldStopAFileAlreadyBeingCopied() throws Exception {
        byte[] content = randomBytes(LARGE_FILE_SIZE);
        Path file = createFile("large.bin", content);

        CountDownLatch copyStarted = new CountDownLatch(1);
        CountDownLatch cancelSent = new CountDownLatch(1);

        // Holds the copy after its first chunk until the test has cancelled
        fileOperations = new FileOperations() {
            @Override
            InputStream openFile(String absolutePath) throws IOException {
                return new FilterInputStream(super.openFile(absolutePath)) {
                    private int reads = 0;

                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        if (++reads == 2) {
                            copyStarted.countDown();
                            awaitOrFail(cancelSent);
                        }
                        return super.read(b, off, len);
                    }
                };
            }
        };
        fileOperations.inputPath = inputDir + "/";

        // Zip on another thread, as the executor would, and cancel from this one
        CompletableFuture<Void> zipping =
            CompletableFuture.runAsync(() -> zipAll(List.of(file.toString())));
        awaitOrFail(copyStarted);
        job.cancelled = true;
        cancelSent.countDown();
        zipping.get(5, TimeUnit.SECONDS);

        // Part of the file made it in before the cancel, the rest did not
        byte[] zipped = readOutput().get("large.bin");
        assertThat(zipped.length).isGreaterThan(0).isLessThan(content.length);
        assertThat(zipped).isEqualTo(Arrays.copyOf(content, zipped.length));
    }

    private static void awaitOrFail(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IOException("Timed out waiting");
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    // The tests below cancel just before a path starts copying, the same
    // moment as a cancel landing right after hasNext(). The copy must notice
    // the flag inside the path rather than only once the path is finished.

    @Test
    public void cancelShouldStopInsideALargeFile() throws IOException {
        byte[] content = randomBytes(LARGE_FILE_SIZE);
        Path file = createFile("large.bin", content);

        Iterator<ZipProgress> zipper = fileOperations.zipper(job, List.of(file.toString())).iterator();
        job.cancelled = true;
        ZipProgress progress = zipper.next();

        assertThat(zipper.hasNext()).isFalse();
        assertThat(readOutput().get("large.bin").length).isLessThan(content.length);
        // The interrupted file must not count towards progress
        assertThat(progress.progress()).isZero();
    }

    @Test
    public void cancelShouldStopInsideADirectory() throws IOException {
        createFile("dir/a.txt", "a".getBytes());
        createFile("dir/sub/b.txt", "b".getBytes());

        Iterator<ZipProgress> zipper =
            fileOperations.zipper(job, List.of(inputDir.resolve("dir").toString())).iterator();
        job.cancelled = true;
        zipper.next();

        assertThat(zipper.hasNext()).isFalse();
        assertThat(readOutput()).isEmpty();
    }

    @Test
    public void cancelShouldStopInsideANestedZip() throws IOException {
        Path nested = createNestedZip("maps.zip", "x.txt", "y.txt");

        Iterator<ZipProgress> zipper = fileOperations.zipper(job, List.of(nested.toString())).iterator();
        job.cancelled = true;
        zipper.next();

        assertThat(zipper.hasNext()).isFalse();
        assertThat(readOutput()).isEmpty();
    }

    // Creates a zip under the input directory whose entries contain their own names
    private Path createNestedZip(String name, String... entryNames) throws IOException {
        Path path = inputDir.resolve(name);
        try (OutputStream out = Files.newOutputStream(path);
             ZipOutputStream zout = new ZipOutputStream(out)) {
            for (String entryName : entryNames) {
                zout.putNextEntry(new ZipEntry(entryName));
                zout.write(entryName.getBytes());
                zout.closeEntry();
            }
        }
        return path;
    }
}
