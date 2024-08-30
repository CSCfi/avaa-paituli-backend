package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.download.DownloadGenerator;
import fi.csc.avaa.paituli.download.io.FileSizesException;
import fi.csc.avaa.paituli.model.DownloadRequest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.jboss.logging.Logger;

@ApplicationScoped
public class DownloadService {
    
    private static final Logger LOG = Logger.getLogger(DownloadService.class);

    @Inject
    DownloadGenerator downloadGenerator;

    @Inject
    EmailService emailService;

    @Inject
    LogService logService;

    public CompletableFuture<String> generateDownload(DownloadRequest request) {
        return CompletableFuture.supplyAsync(() -> downloadGenerator.generate(request))
                .whenComplete((downloadUrl, err) -> {
                    if (err != null) {
                        LOG.info("Could not generate download: " + err.getMessage());
                        System.err.println("Could not generate download: " + err.getMessage());
                        LOG.info("Poikkeuksen tyyppi: " + err.getClass().getName());
                        if (err instanceof fi.csc.avaa.paituli.download.io.FileSizesException) {
                            emailService.sendErrorEmail(request, err.getMessage());
                            LOG.info("Send error email");
                        } else {
                            LOG.info("muu virhe tai err instanceof FileSizesException ei toiminut");
                        }

                    } else {
                        emailService.sendEmail(request, downloadUrl);
                        logService.log(request);
                    }
                });
    }
}
