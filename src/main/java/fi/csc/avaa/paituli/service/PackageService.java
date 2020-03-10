package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.model.DownloadRequest;
import io.vertx.axle.core.Vertx;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class PackageService {

    @Inject
    Vertx vertx;

    @ConfigProperty(name = "paituli.download.inputPath")
    String inputPath;

    @ConfigProperty(name = "paituli.download.outputPath")
    String outputPath;

    @ConfigProperty(name = "paituli.download.baseUrl")
    String baseUrl;

    protected CompletionStage<String> generatePackage(DownloadRequest request) {
        return request.downloadType.equals(DownloadType.ZIP)
                ? generateZipPackage(request)
                : generateFileList(request);
    }

    private CompletionStage<String> generateZipPackage(DownloadRequest request) {
        return vertx.executeBlocking(promise -> {
            doBlockingOperation();
            promise.complete("http://example.com/file.zip");
        });
    }

    private CompletionStage<String> generateFileList(DownloadRequest request) {
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
