package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.model.DownloadRequest;
import fi.csc.avaa.paituli.util.EmailUtils;

import javax.enterprise.context.ApplicationScoped;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class DownloadService {

    ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 10, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    public void generateDownload(DownloadRequest downloadRequest) {
        executor.execute(() -> {
            if (downloadRequest.downloadType == DownloadType.LIST) {

            } else if (downloadRequest.downloadType == DownloadType.ZIP) {

            }
            String[] filenames = { "TODO1.zip", "TODO2.zip" };
            EmailUtils.sendEmail("fi", downloadRequest, Arrays.asList(filenames), "http://example.com/TODOS.zip");
        });
    }
}
