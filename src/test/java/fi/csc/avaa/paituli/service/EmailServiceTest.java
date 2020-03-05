package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.model.DownloadRequest;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
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
    public void shouldSendEmailWithFilledTemplate() {
        DownloadRequest request = new DownloadRequest();
        request.email = "test@example.com";
        request.data = "testdata";
        request.year = "1999";
        String filename1 = "test1.zip";
        String filename2 = "test2.zip";
        String zipUrl = "http://example.com/test.zip";
        emailService.sendEmail("fi", request, Arrays.asList(filename1, filename2), zipUrl);

        List<Mail> sent = mailbox.getMessagesSentTo(request.email);
        assertThat(sent).hasSize(1);
        Mail mail = sent.get(0);
        assertThat(mail.getTo())
                .hasSize(1)
                .contains(request.email);
        assertThat(mail.getHtml())
                .contains(request.data, request.year, filename1, filename2, zipUrl);
    }
}
