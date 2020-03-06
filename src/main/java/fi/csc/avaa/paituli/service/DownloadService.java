package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.model.DownloadRequest;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.concurrent.*;

@ApplicationScoped
public class DownloadService {

    @Inject
    EmailService emailService;

    public void generateDownload(DownloadRequest downloadRequest) {
        CompletableFuture.runAsync(() -> {
            if (downloadRequest.downloadType == DownloadType.LIST) {

            } else if (downloadRequest.downloadType == DownloadType.ZIP) {

            }
            String[] filenames = {"TODO1.zip", "TODO2.zip"};
            emailService.sendEmail("fi", downloadRequest,
                    Arrays.asList(filenames), "http://example.com/TODOS.zip");
        });
    }
}
