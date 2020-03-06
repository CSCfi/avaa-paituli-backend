package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.model.DownloadRequest;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Arrays;

@ApplicationScoped
public class DownloadService {

    @Inject
    PackageService packageService;

    @Inject
    EmailService emailService;

    public void generateDownload(DownloadRequest request) {
        packageService.generatePackage(request)
                .whenComplete((zipUrl, err) -> {
                    if (err != null) {
                        System.err.println("Could not generate package: " + err.getMessage());
                    } else {
                        String[] filenames = {"TODO1.zip", "TODO2.zip"};
                        emailService.sendEmail("fi", request, Arrays.asList(filenames), zipUrl);
                        // TODO logging
                    }
                });
    }
}
