package fi.csc.avaa.paituli.service;

import fi.csc.avaa.paituli.entity.LogEvent;
import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.model.DownloadRequest;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

@ApplicationScoped
public class LogService {

    @ConfigProperty(name = "paituli.log.salt")
    String salt;

    @Transactional
    public void log(DownloadRequest request) {
        String organization = request.email.substring(request.email.indexOf('@') + 1);
        String saltedHash = hash(request.email);
        LogEvent logEvent = new LogEvent();
        logEvent.timestamp = new Date();
        logEvent.saltedhash = saltedHash;
        logEvent.organization = organization;
        logEvent.dataset = request.data_id;
        logEvent.numberOfFiles = request.filenames.size() - 1;
        // logEvent.downloadType = 3;
        logEvent.downloadType = convertDownloadType(request.downloadType);
        logEvent.persist();
    }

    public Integer convertDownloadType(DownloadType type) {
        try {
            return type.equals(DownloadType.ZIP)
                    ? 1
                    : 2;
            }
            catch (Exception e) {
                System.err.println(type);
                System.err.println(DownloadType.ZIP);
            }

    }

    private String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = salt + s;
            md.update(input.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            return String.format("%064x", new BigInteger(1, digest));
        } catch (NoSuchAlgorithmException e) {
            System.err.println("SHA-256 algorithm not available");
        }
        return "";
    }
}
