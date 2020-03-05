package fi.csc.avaa.paituli.util;

import fi.csc.avaa.paituli.constants.Constants;
import fi.csc.avaa.paituli.model.DownloadRequest;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.StringJoiner;

public final class EmailUtils {

    public static void sendEmail(String language, DownloadRequest request, List<String> filenameList, String zipUrl) {
        ResourceBundle messages = ResourceBundle.getBundle("messages", Locale.forLanguageTag(language));
        String fromAddress = messages.getString(Constants.MESSAGE_KEY_EMAIL_FROM_ADDRESS);
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

        System.out.println(fromAddress);
        System.out.println(subject);
        System.out.println(body);
    }
}
