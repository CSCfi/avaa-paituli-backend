package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.model.DownloadRequest;
import io.vertx.axle.core.Vertx;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class PackageService {

    @Inject
    Vertx vertx;

    protected CompletionStage<String> generatePackage(DownloadRequest request) {
        return request.downloadType.equals(DownloadType.ZIP)
                ? generateZipPackage(request)
                : generateListPackage(request);
    }

    private CompletionStage<String> generateZipPackage(DownloadRequest request) {
        return vertx.executeBlocking(promise -> {
            doBlockingOperation();
            promise.complete("http://example.com/file.zip");
        });
    }

    private CompletionStage<String> generateListPackage(DownloadRequest request) {
        return vertx.executeBlocking(promise -> {
            doBlockingOperation();
            promise.complete("http://example.com/list.zip");
        });
    }

    private void doBlockingOperation() {
        System.out.println("Generating package...");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
