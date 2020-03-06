package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.model.DownloadRequest;
import io.quarkus.test.Mock;

import javax.enterprise.context.ApplicationScoped;

@Mock
@ApplicationScoped
public class MockDownloadService extends DownloadService {

    private DownloadRequest downloadRequest;

    @Override
    public void generateDownload(DownloadRequest request) {
        this.downloadRequest = request;
    }

    public DownloadRequest getDownloadRequest() {
        return downloadRequest;
    }
}
