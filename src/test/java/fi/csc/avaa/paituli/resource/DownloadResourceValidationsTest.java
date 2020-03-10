package fi.csc.avaa.paituli.resource;

import fi.csc.avaa.paituli.constants.Constants;
import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.model.DownloadRequest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
public class DownloadResourceValidationsTest {

    @Test
    public void shouldReturn400WithMissingEmail() {
        DownloadRequest request = new DownloadRequest();
        request.filePaths = "test";
        request.downloadType = DownloadType.ZIP;

        testValidationError(request, "must not be blank", "email");
    }

    @Test
    public void shouldReturn400WithMissingFilePaths() {
        DownloadRequest request = new DownloadRequest();
        request.email = "test@example.com";
        request.downloadType = DownloadType.ZIP;

        testValidationError(request, "must not be blank", "filePaths");
    }

    @Test
    public void shouldReturn400WithMissingDownloadType() {
        DownloadRequest request = new DownloadRequest();
        request.email = "test@example.com";
        request.filePaths = "test";

        testValidationError(request, "must not be null", "downloadType");
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
