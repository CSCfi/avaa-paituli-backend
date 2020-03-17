package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.model.DownloadRequest;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@Tag("integration")
public class EmailServiceTest {

    @Inject
    EmailService emailService;

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    public void init() {
        mailbox.clear();
    }

    @Test
    public void shouldSendEmailWithFilledTemplate() throws ExecutionException, InterruptedException {
        final String filename1 = "test1.zip";
        final String filename2 = "test2.zip";
        final String downloadUrl = "http://example.com/test.zip";
        final DownloadRequest request = new DownloadRequest();
        request.filePaths = Arrays.asList(filename1, filename2);
        request.email = "test@example.com";
        request.data = "Kuukauden sademäärä, 1km";
        request.org = "Ilmatieteen laitos";
        request.year = "1961-2014";
        request.scale = "1 km x 1 km";
        request.coordsys = "ETRS-TM35FIN";
        request.format = "TIFF";

        CompletionStage<Response> completionStage = emailService.sendEmail("fi", request, downloadUrl);
        // block
        completionStage.toCompletableFuture().get();

        List<Mail> sent = mailbox.getMessagesSentTo(request.email);
        assertThat(sent).hasSize(1);
        Mail mail = sent.get(0);
        assertThat(mail.getTo())
                .hasSize(1)
                .contains(request.email);
        assertThat(mail.getHtml())
                .contains(request.data,
                        request.year,
                        request.org,
                        request.year,
                        request.scale,
                        request.coordsys,
                        request.format,
                        filename1,
                        filename2,
                        downloadUrl);
    }
}
