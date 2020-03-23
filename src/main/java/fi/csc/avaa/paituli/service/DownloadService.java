package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.download.PackageGenerator;
import fi.csc.avaa.paituli.download.UrlListGenerator;
import fi.csc.avaa.paituli.model.DownloadRequest;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class DownloadService {

    @Inject
    PackageGenerator packageGenerator;

    @Inject
    UrlListGenerator urlListGenerator;

    @Inject
    EmailService emailService;

    @Inject
    LogService logService;

    public CompletableFuture<String> download(DownloadRequest request) {
        return CompletableFuture.supplyAsync(() ->
                request.downloadType.equals(DownloadType.ZIP)
                        ? packageGenerator.generate(request.filePaths)
                        : urlListGenerator.generate(request.filePaths)
        ).whenComplete((downloadUrl, err) -> {
            if (err != null) {
                System.err.println("Could not generate download: " + err.getMessage());
            } else {
                emailService.sendEmail(Locale.forLanguageTag("fi"), request, downloadUrl);
                logService.log(request);
            }
        });
    }
}
