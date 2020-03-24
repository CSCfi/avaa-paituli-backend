package fi.csc.avaa.paituli.rest;

import fi.csc.avaa.paituli.constants.Constants;
import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.model.DownloadRequest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@Tag("integration")
public class DownloadResourceValidationsTest {

    @Test
    public void shouldReturn400WithMissingEmail() {
        DownloadRequest request = new DownloadRequest();
        request.filePaths = Collections.singletonList("test");
        request.filenames = Collections.singletonList("test");
        request.downloadType = DownloadType.ZIP;

        testValidationError(request, "must not be blank", "email");
    }

    @Test
    public void shouldReturn400WithMissingFilePaths() {
        DownloadRequest request = new DownloadRequest();
        request.email = "test@example.com";
        request.filenames = Collections.singletonList("test");
        request.downloadType = DownloadType.ZIP;

        testValidationError(request, "must not be empty", "filePaths");
    }

    @Test
    public void shouldReturn400WithEmptyFilePaths() {
        DownloadRequest request = new DownloadRequest();
        request.email = "test@example.com";
        request.downloadType = DownloadType.ZIP;
        request.filePaths = Collections.emptyList();
        request.filenames = Collections.singletonList("test");

        testValidationError(request, "must not be empty", "filePaths");
    }

    @Test
    public void shouldReturn400WithMissingDownloadType() {
        DownloadRequest request = new DownloadRequest();
        request.email = "test@example.com";
        request.filePaths = Collections.singletonList("test");
        request.filenames = Collections.singletonList("test");

        testValidationError(request, "must not be null", "downloadType");
    }

    @Test
    public void shouldReturn400WithMissingFilenames() {
        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;
        request.email = "test@example.com";
        request.filePaths = Collections.singletonList("test");

        testValidationError(request, "must not be empty", "filenames");
    }

    @Test
    public void shouldReturn400WithEmptyFilenames() {
        DownloadRequest request = new DownloadRequest();
        request.downloadType = DownloadType.ZIP;
        request.email = "test@example.com";
        request.filePaths = Collections.singletonList("test");
        request.filenames = Collections.emptyList();

        testValidationError(request, "must not be empty", "filenames");
    }

    private void testValidationError(DownloadRequest request, String expectedMessage, String field) {
        given()
                .when()
                .contentType("application/json")
                .body(request)
                .post(Constants.PATH_DOWNLOAD)
                .then()
                .statusCode(400)
                .assertThat()
                .body("parameterViolations[0].message", message -> equalTo(expectedMessage))
                .body("parameterViolations[0].path", path -> endsWith(field));
    }
}
