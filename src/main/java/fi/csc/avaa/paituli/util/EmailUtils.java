package fi.csc.avaa.paituli.util;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

public class EmailUtils {

    public static void sendEmail() {
        try {
            URI uri = EmailUtils.class.getClassLoader().getResource("email/template_fi").toURI();
            String template = new String(Files.readAllBytes(Paths.get(uri)), Charset.forName("utf-8"));
            // MessageFormat.format(....
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
