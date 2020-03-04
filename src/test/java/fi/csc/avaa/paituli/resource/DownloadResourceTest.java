package fi.csc.avaa.paituli.resource;

import fi.csc.avaa.paituli.constants.Constants;
import fi.csc.avaa.paituli.model.DownloadRequest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
public class DownloadResourceTest {

    public static final String APPLICATION_JSON = "application/json";

    @Test
    public void shouldReturn200WithValidDownloadRequest() {
        DownloadRequest request = new DownloadRequest();
        request.filePaths = "test";
        request.email = "test@example.com";

        given()
                .when()
                .contentType(APPLICATION_JSON)
                .body(request)
                .post(Constants.PATH_DOWNLOAD)
                .then()
                .statusCode(200);
    }

    @Test
    public void shouldReturn400WithMissingEmail() {
        DownloadRequest request = new DownloadRequest();
        request.filePaths = "test";

        given()
                .when()
                .contentType(APPLICATION_JSON)
                .body(request)
                .post(Constants.PATH_DOWNLOAD)
                .then()
                .statusCode(400)
                .assertThat()
                .body("parameterViolations[0].message", message -> equalTo("must not be blank"))
                .body("parameterViolations[0].path", path -> endsWith("email"));
    }

    @Test
    public void shouldReturn400WithMissingFilePaths() {
        DownloadRequest request = new DownloadRequest();
        request.email = "test@example.com";

        given()
                .when()
                .contentType(APPLICATION_JSON)
                .body(request)
                .post(Constants.PATH_DOWNLOAD)
                .then()
                .statusCode(400)
                .assertThat()
                .body("parameterViolations[0].message", message -> equalTo("must not be blank"))
                .body("parameterViolations[0].path", path -> endsWith("filePaths"));
    }
}
