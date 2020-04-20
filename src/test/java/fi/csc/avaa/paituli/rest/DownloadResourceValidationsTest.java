package fi.csc.avaa.paituli.rest;

import fi.csc.avaa.paituli.constants.Constants;
import fi.csc.avaa.paituli.constants.DownloadType;
import fi.csc.avaa.paituli.model.DownloadRequest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItems;

@QuarkusTest
@Tag("integration")
public class DownloadResourceValidationsTest {

    @Test
    public void shouldReturn200WithValidRequest() {
        DownloadRequest request = new DownloadRequest();
        request.email = "test@example.com";
        request.filePaths = Collections.singletonList("test");
        request.filenames = Collections.singletonList("test");
        request.downloadType = DownloadType.ZIP;
        request.locale = "fi_FI";

        given()
                .when()
                .contentType("application/json")
                .body(request)
                .post(Constants.PATH_DOWNLOAD)
                .then()
                .statusCode(200);
    }

    @Test
    public void shouldReturn400AndParameterViolations() {
        DownloadRequest request = new DownloadRequest();
        given()
                .when()
                .contentType("application/json")
                .body(request)
                .post(Constants.PATH_DOWNLOAD)
                .then()
                .statusCode(400)
                .assertThat()
                .body("parameterViolations.message", message -> hasItems("must not be null", "must not be empty"))
                .body("parameterViolations.path", path -> hasItems("generateDownload.downloadRequest.email",
                        "generateDownload.downloadRequest.filePaths", "generateDownload.downloadRequest.locale",
                        "generateDownload.downloadRequest.filenames", "generateDownload.downloadRequest.downloadType"));
    }
}
