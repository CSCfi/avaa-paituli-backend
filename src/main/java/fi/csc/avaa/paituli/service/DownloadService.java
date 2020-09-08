package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.download.DownloadGenerator;
import fi.csc.avaa.paituli.model.DownloadRequest;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class DownloadService {

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
                        System.err.println("Could not generate download: " + err.getMessage());
                    } else {
                        emailService.sendEmail(request, downloadUrl);
                        logService.log(request);
                    }
                });
    }
}
