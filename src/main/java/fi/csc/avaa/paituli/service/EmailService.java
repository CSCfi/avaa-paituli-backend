package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.constants.Constants;
import fi.csc.avaa.paituli.model.DownloadRequest;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.ReactiveMailer;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.StringJoiner;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class EmailService {

    @Inject
    ReactiveMailer mailer;

    protected CompletionStage<Response> sendEmail(String language, DownloadRequest request,
                                               List<String> filenameList, String zipUrl) {
        ResourceBundle messages = ResourceBundle.getBundle("messages", Locale.forLanguageTag(language));
        String subject = messages.getString(Constants.MESSAGE_KEY_EMAIL_SUBJECT);
        String template = messages.getString(Constants.MESSAGE_KEY_EMAIL_BODY_TEMPLATE);

        StringJoiner datasetInfo = new StringJoiner(", ");
        if (request.org != null) datasetInfo.add(request.org);
        if (request.data != null) datasetInfo.add(request.data);
        if (request.scale != null) datasetInfo.add(request.scale);
        if (request.year != null) datasetInfo.add(request.year);
        if (request.coordsys != null) datasetInfo.add(request.coordsys);
        if (request.format != null) datasetInfo.add(request.format);

        String filenames = "<br>" + String.join("<br>", filenameList) + ".";
        String body = MessageFormat.format(template, datasetInfo.toString(), filenames, zipUrl);

        return mailer.send(Mail.withHtml(request.email, subject, body)).thenApply(x -> Response.accepted().build());
    }
}
