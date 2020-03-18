package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.download.FileListGenerator;
import fi.csc.avaa.paituli.download.PackageGenerator;
import fi.csc.avaa.paituli.model.DownloadRequest;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class DownloadService {

    @Inject
    PackageGenerator downloadPackageGenerator;

    @Inject
    FileListGenerator fileListGenerator;

    @Inject
    EmailService emailService;

    @Inject
    LogService logService;

    public void download(DownloadRequest request) {
        CompletableFuture.supplyAsync(() ->
                request.downloadType.equals(DownloadType.ZIP)
                        ? downloadPackageGenerator.generate(request.filePaths)
                        : fileListGenerator.generate(request.filePaths)
        ).whenComplete((downloadUrl, err) -> {
            if (err != null) {
                System.err.println("Could not generate download: " + err.getMessage());
            } else {
                emailService.sendEmail("fi", request, downloadUrl);
                logService.log(request);
            }
        });
    }
}
