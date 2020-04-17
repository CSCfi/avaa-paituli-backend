package fi.csc.avaa.paituli.constants;

import java.util.Locale;

public final class Constants {

    public static final String EXTENSION_JP2 = "jp2";
    public static final String EXTENSION_TXT = "txt";
    public static final String EXTENSION_ZIP = "zip";

    public static final Locale LOCALE_FI = Locale.forLanguageTag("fi");
    public static final Locale LOCALE_EN = Locale.forLanguageTag("en");

    public static final String MSG_PACKAGE_EMAIL_SUBJECT = "package_email_subject";
    public static final String MSG_PACKAGE_EMAIL_BODY_TEMPLATE = "package_email_body_template";
    public static final String MSG_URL_LIST_EMAIL_SUBJECT = "url_list_email_subject";
    public static final String MSG_URL_LIST_EMAIL_BODY_TEMPLATE = "url_list_email_body_template";

    public static final String PATH_DATASETS = "/datasets/{locale}";
    public static final String PATH_DOWNLOAD = "/download";
}
