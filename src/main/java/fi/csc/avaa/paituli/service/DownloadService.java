package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.download.FileListGenerator;
import fi.csc.avaa.paituli.download.PackageGenerator;
import fi.csc.avaa.paituli.email.EmailSender;
import fi.csc.avaa.paituli.model.DownloadRequest;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class DownloadService {

    @Inject
    PackageGenerator downloadPackageGenerator;

    @Inject
    FileListGenerator fileListGenerator;

    @Inject
    EmailSender emailSender;

    public void createDownload(DownloadRequest request) {
        CompletableFuture.supplyAsync(() -> {
            String[] filePaths = request.filePaths.split(";");
            return request.downloadType.equals(DownloadType.ZIP)
                    ? downloadPackageGenerator.generate(filePaths)
                    : fileListGenerator.generate(filePaths);
        }).whenComplete((downloadUrl, err) -> {
            if (err != null) {
                System.err.println("Could not generate download: " + err.getMessage());
            } else {
                String[] filenames = {"TODO1.zip", "TODO2.zip"};
                emailSender.sendEmail("fi", request, Arrays.asList(filenames), downloadUrl);
                // TODO logging
            }
        });
    }
}
