package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.constants.Constants;
import fi.csc.avaa.paituli.constants.DownloadType;
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
import java.util.Locale;
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
    public void shouldSendPackageEmailWithFilledTemplateFi() throws ExecutionException, InterruptedException {
        verifyPackageEmail("fi");
    }

    @Test
    public void shouldSendPackageEmailWithFilledTemplateEn() throws ExecutionException, InterruptedException {
        verifyPackageEmail("en");
    }

    @Test
    public void shouldSendUrlListEmailWithFilledTemplateFi() throws ExecutionException, InterruptedException {
        verifyUrlListEmail("fi");
    }

    @Test
    public void shouldSendUrlListEmailWithFilledTemplateEn() throws ExecutionException, InterruptedException {
        verifyUrlListEmail("en");
    }

    private void verifyPackageEmail(String languageTag) throws ExecutionException, InterruptedException {
        final String filename1 = "test1.zip";
        final String filename2 = "test2.zip";
        final String downloadUrl = "http://example.com/test.zip";
        final DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;
        request.filenames = Arrays.asList(filename1, filename2);
        request.email = "test@example.com";
        request.locale = languageTag;
        request.data = "Kuukauden sademäärä, 1km";
        request.org = "Ilmatieteen laitos";
        request.year = "1961-2014";
        request.scale = "1 km x 1 km";
        request.coordsys = "ETRS-TM35FIN";
        request.format = "TIFF";

        CompletionStage<Response> completionStage = emailService.sendEmail(request, downloadUrl);
        completionStage.toCompletableFuture().get();

        List<Mail> sent = mailbox.getMessagesSentTo(request.email);
        assertThat(sent).hasSize(1);
        Mail mail = sent.get(0);
        boolean isLocaleFi = Locale.forLanguageTag(languageTag).equals(Constants.LOCALE_FI);
        assertThat(mail.getTo())
                .hasSize(1)
                .contains(request.email);
        assertThat(mail.getSubject())
                .contains(isLocaleFi ? "lataustiedosto on valmiina" : "data download is available");
        assertThat(mail.getHtml())
                .contains(isLocaleFi ? "Ladattava aineisto" : "You have ordered",
                        request.data,
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

    private void verifyUrlListEmail(String languageTag) throws ExecutionException, InterruptedException {
        final String downloadUrl = "http://example.com/test.zip";
        final DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.LIST;
        request.email = "test@example.com";
        request.locale = languageTag;
        request.data = "Kuukauden sademäärä, 1km";
        request.org = "Ilmatieteen laitos";
        request.year = "1961-2014";
        request.scale = "1 km x 1 km";
        request.coordsys = "ETRS-TM35FIN";
        request.format = "TIFF";

        CompletionStage<Response> completionStage = emailService.sendEmail(request, downloadUrl);
        completionStage.toCompletableFuture().get();

        List<Mail> sent = mailbox.getMessagesSentTo(request.email);
        assertThat(sent).hasSize(1);
        Mail mail = sent.get(0);
        boolean isLocaleFi = Locale.forLanguageTag(languageTag).equals(Constants.LOCALE_FI);
        assertThat(mail.getTo())
                .hasSize(1)
                .contains(request.email);
        assertThat(mail.getSubject())
                .contains(isLocaleFi ? "tiedostolista on valmiina" : "data download is available");
        assertThat(mail.getHtml())
                .contains(isLocaleFi ? "Ladattava aineisto" : "You have ordered",
                        request.data,
                        request.year,
                        request.org,
                        request.year,
                        request.scale,
                        request.coordsys,
                        request.format,
                        downloadUrl);
    }
}
