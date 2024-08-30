package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.constants.Constants;
import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.model.DownloadRequest;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.StringJoiner;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@ApplicationScoped
public class EmailService {

    @Inject
    ReactiveMailer mailer;

    public CompletionStage<Response> sendEmail(DownloadRequest request, String downloadUrl) {
        StringJoiner datasetInfo = new StringJoiner(", ");
        if (request.org != null) datasetInfo.add(request.org);
        if (request.data != null) datasetInfo.add(request.data);
        if (request.scale != null) datasetInfo.add(request.scale);
        if (request.year != null) datasetInfo.add(request.year);
        if (request.coordsys != null) datasetInfo.add(request.coordsys);
        if (request.format != null) datasetInfo.add(request.format);

        ResourceBundle messages = ResourceBundle.getBundle("messages", Locale.forLanguageTag(request.locale));
        Mail mail = request.downloadType.equals(DownloadType.ZIP)
                ? getPackageMail(request, downloadUrl, datasetInfo, messages)
                : getUrlListMail(request, downloadUrl, datasetInfo, messages);
        return mailer.send(mail)
                .subscribeAsCompletionStage()
                .thenApply(x -> Response.accepted().build());
    }

    private Mail getPackageMail(DownloadRequest request, String downloadUrl, StringJoiner datasetInfo,
                                ResourceBundle messages) {
        String subject = messages.getString(Constants.MSG_PACKAGE_EMAIL_SUBJECT);
        String template = messages.getString(Constants.MSG_PACKAGE_EMAIL_BODY_TEMPLATE);
        String filenames = request.filenames
                .stream()
                .sorted()
                .collect(Collectors.joining("<br>", "<br>", "."));
        String body = MessageFormat.format(template, datasetInfo, filenames, downloadUrl);
        return Mail.withHtml(request.email, subject, body);
    }

    private Mail getUrlListMail(DownloadRequest request, String downloadUrl, StringJoiner datasetInfo,
                                ResourceBundle messages) {
        String subject = messages.getString(Constants.MSG_URL_LIST_EMAIL_SUBJECT);
        String template = messages.getString(Constants.MSG_URL_LIST_EMAIL_BODY_TEMPLATE);
        String body = MessageFormat.format(template, datasetInfo, downloadUrl);
        return Mail.withHtml(request.email, subject, body);
    }

     public CompletionStage<Response> sendErrorEmail(DownloadRequest request, String errormessage) {
        ResourceBundle messages = ResourceBundle.getBundle("messages", Locale.forLanguageTag(request.locale));
        String subject = messages.getString(Constants.ERROR_MSG_EMAIL_SUBJECT);
        String template = messages.getString(Constants.ERROR_MSG_EMAIL_BODY_TEMPLATE);
        String filenames = request.filenames
                .stream()
                .sorted()
                .collect(Collectors.joining("<br>", "<br>", "."));
        String body = MessageFormat.format(template, errormessage);
        Mail mail =  Mail.withText(request.email, subject, body);
        return mailer.send(mail)
                .subscribeAsCompletionStage()
                .thenApply(x -> Response.accepted().build());
     }

}
