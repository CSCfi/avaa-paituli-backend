package fi.csc.avaa.paituli.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;

import fi.csc.avaa.paituli.download.DownloadGenerator;
import fi.csc.avaa.paituli.model.DownloadJob;
import fi.csc.avaa.paituli.model.DownloadRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DownloadService {
    
    @Inject
    DownloadGenerator downloadGenerator;

    @Inject
    ManagedExecutor managedExecutor;

    @ConfigProperty(name = "paituli.download.filePrefix")
    public String filePrefix;

    @ConfigProperty(name = "paituli.download.outputPath")
    public String outputPath;


    // Job storage (could later move to DB/Redis)
    private final Map<String, DownloadJob> jobs = new ConcurrentHashMap<>();

    // Creates a new download job, starts its thread and return the job 
    public DownloadJob createDownloadJob(DownloadRequest request) {

        // Create a job for the request and store it
        DownloadJob job = new DownloadJob(request, filePrefix, outputPath);
        jobs.put(job.ID, job);

        // Start processing immediately.
        // Alternatively, here we could implement scheduling, priority, etc.
        managedExecutor.runAsync(() -> {
            downloadGenerator.processJob(job);
        });
        return job;
    }

    public DownloadJob getJob(String jobId) {
        return jobs.get(jobId);
    }
}