package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.model.DownloadRequest;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class DownloadService {

    @Inject
    EmailService emailService;

    ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 10, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    public void generateDownload(DownloadRequest downloadRequest) {
        executor.execute(() -> {
            if (downloadRequest.downloadType == DownloadType.LIST) {

            } else if (downloadRequest.downloadType == DownloadType.ZIP) {

            }
            String[] filenames = {"TODO1.zip", "TODO2.zip"};
            emailService.sendEmail("fi", downloadRequest,
                    Arrays.asList(filenames), "http://example.com/TODOS.zip");
        });
    }
}
