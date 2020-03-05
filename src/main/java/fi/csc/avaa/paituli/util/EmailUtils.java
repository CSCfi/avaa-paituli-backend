package fi.csc.avaa.paituli.util;

import fi.csc.avaa.paituli.constants.Constants;

import java.util.Locale;
import java.util.ResourceBundle;

public class EmailUtils {

    public static void sendEmail(String language) {
        ResourceBundle messages = ResourceBundle.getBundle("messages", Locale.forLanguageTag(language));
        String fromAddress = messages.getString(Constants.MESSAGE_KEY_EMAIL_FROM_ADDRESS);
        String subject = messages.getString(Constants.MESSAGE_KEY_EMAIL_SUBJECT);
        String body = messages.getString(Constants.MESSAGE_KEY_EMAIL_BODY_TEMPLATE);

    }
}
