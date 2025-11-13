package fi.csc.avaa.paituli.model;

import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class DownloadJob {
    // A container class for tracking download job process
    // and defining their inputs (request) and outputs

    public final String ID;
    public final DownloadRequest request;

    public final String outputFilename;
    public final String outputFilePath;

    public volatile double progress; // from 0.0 to 1.0
    public volatile String error;

    public DownloadJob(
        DownloadRequest request,
        String filePrefix,
        String outputPath) {

        this.ID = UUID.randomUUID().toString();
        this.request = request;

        String ext = request.downloadType.getExtension();
        String randomNumbers = new Random().ints(8, 0, 10)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining());

        this.outputFilename = String.format("%s%s.%s", filePrefix, randomNumbers, ext);
        this.outputFilePath = String.format("%s/%s", outputPath, this.outputFilename);

        this.progress = 0;
        this.error = "";
    }

    public boolean processing() { return this.progress < 1.0; }

    @Override
    public String toString()
    {
        return String.format("[Job %s - %.0f%%]",
            this.ID.substring(0,8),
            this.progress * 100);
    }
}