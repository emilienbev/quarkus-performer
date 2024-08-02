package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class CouchbaseResourceTest {
    @Test
    void testHelloEndpoint() {
        given()
                .when().get("/couchbase")
                .then()
                .statusCode(200)
                .body(is("Success!"));
    }
}