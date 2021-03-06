/*
 * Copyright 2016 Stormpath, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stormpath.tck.oauth2

import com.jayway.restassured.http.ContentType
import com.jayway.restassured.response.Response
import com.stormpath.tck.AbstractIT
import com.stormpath.tck.responseSpecs.JsonResponseSpec
import com.stormpath.tck.util.JwtUtils
import com.stormpath.tck.util.TestAccount
import org.apache.commons.codec.binary.Base64
import org.testng.annotations.Test

import static com.jayway.restassured.RestAssured.get
import static com.jayway.restassured.RestAssured.given
import static com.stormpath.tck.util.FrameworkConstants.OauthRoute
import static com.stormpath.tck.util.TestAccount.Mode.WITHOUT_DISPOSABLE_EMAIL
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.equalToIgnoringCase
import static org.hamcrest.Matchers.is
import static org.hamcrest.Matchers.isEmptyOrNullString
import static org.hamcrest.Matchers.not
import static org.hamcrest.Matchers.nullValue
import static org.testng.Assert.*

class Oauth2IT extends AbstractIT {


    final String clientCredentialsId = Base64.encodeBase64String(UUID.randomUUID().toString().getBytes())
    final String clientCredentialsSecret = Base64.encodeBase64String(UUID.randomUUID().toString().getBytes())

    /** Unsupported grant type returns error
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/6">#6</a>
     */
    @Test(groups=["v100", "json"])
    void oauthErrorsOnUnsupportedGrantTypes() throws Exception {

        given()
            .param("grant_type", "foobar_grant")
        .when()
            .post(OauthRoute)
        .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("error", is("unsupported_grant_type"))
    }

    /** Missing grant type returns error
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/7">#7</a>
     */
    @Test(groups=["v100", "json"])
    void oauthErrorsOnMissingGrantType() throws Exception {
        given()
            .param("grant_type", "")
        .when()
            .post(OauthRoute)
        .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("error", is("invalid_request"))
    }

    /** POST must include form parameters
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/15">#15</a>
     */
    @Test(groups=["v100", "json"])
    void oauthErrorsOnJsonRequestBody() throws Exception {
        given()
            .contentType(ContentType.JSON)
            .body([ "hello": "world" ])
        .when()
            .post(OauthRoute)
        .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("error", is("invalid_request"))
    }

    /** POST must include form parameters
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/15">#15</a>
     */
    @Test(groups=["v100", "json"])
    void oauthErrorsOnEmptyRequestBody() throws Exception {
        given()
            .body()
        .when()
            .post(OauthRoute)
        .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("error", is("invalid_request"))
    }

    /** GET should return 405
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/16">#16</a>
     */
    @Test(groups=["v100", "json"])
    void oauthDoesntSupportGet() throws Exception {
        get(OauthRoute)
            .then()
            .assertThat().statusCode(405)
    }

    /** Password grant flow with username/password
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/11">#11</a>
     */
    @Test(groups=["v100", "json"])
    void oauthPasswordGrantWithUsernameSucceeds() throws Exception {

        def account = createTestAccount()
        String accessToken =
            given()
                .param("grant_type", "password")
                .param("username", account.username)
                .param("password", account.password)
            .when()
                .post(OauthRoute)
            .then()
                .spec(JsonResponseSpec.validAccessAndRefreshTokens())
            .extract()
                .path("access_token")

        assertTrue isAccountSubInClaim(account, accessToken)
    }

    /** Password grant flow with username/password and access_token cookie present
     *
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/11">#11</a>
     * @see <a href="https://github.com/stormpath/stormpath-sdk-java/issues/612">#612</a>
     */
    @Test(groups=["v100", "json"])
    void oauthPasswordGrantWithUsernameSucceedsAndCookiePresent() throws Exception {

        def account = createTestAccount()
        def cookies = createSession(account)

        // Okta rate limiting on user/password requests
        rateLimitSleep()

        // @formatter:off
        String accessToken =
                given()
                    .cookies(cookies)
                    .param("grant_type", "password")
                    .param("username", account.username)
                    .param("password", account.password)
                .when()
                     .post(OauthRoute)
                .then()
                     .spec(JsonResponseSpec.validAccessAndRefreshTokens())
                 .extract()
                     .path("access_token")
        // @formatter:on
        assertTrue isAccountSubInClaim(account, accessToken)
    }

    /** Password grant flow with email/password
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/18">#18</a>
     */
    @Test(groups=["v100", "json"])
    void oauthPasswordGrantWithEmailSucceeds() throws Exception {

        def account = createTestAccount()
        String accessToken =
            given()
                .param("grant_type", "password")
                .param("username", account.email)
                .param("password", account.password)
            .when()
                .post(OauthRoute)
            .then()
                .spec(JsonResponseSpec.validAccessAndRefreshTokens())
            .extract()
                .path("access_token")

        assertTrue isAccountSubInClaim(account, accessToken)
    }

    /** Refresh grant flow
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/205">#205</a>
     */
    @Test(groups=["v100", "json"])
    void oauthRefreshGrantWorksWithValidToken() throws Exception {

        def account = createTestAccount()
        Response passwordGrantResponse =
            given()
                .param("grant_type", "password")
                .param("username", account.email)
                .param("password", account.password)
            .when()
                .post(OauthRoute)
            .then()
                .spec(JsonResponseSpec.validAccessAndRefreshTokens())
            .extract()
                .response()

        String accessToken = passwordGrantResponse.path("access_token")
        String refreshToken = passwordGrantResponse.path("refresh_token")

        String newAccessToken =
            given()
                .param("grant_type", "refresh_token")
                .param("refresh_token", refreshToken)
            .when()
                .post(OauthRoute)
            .then()
                .spec(JsonResponseSpec.validAccessAndRefreshTokens())
            .extract()
                .path("access_token")

        assertNotEquals(accessToken, newAccessToken, "The new access token should not equal to the old access token")
        assertTrue isAccountSubInClaim(account, accessToken)
    }

    /** Refresh grant flow should fail without valid refresh token
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/205">#205</a>
     */
    @Test(groups=["v100", "json"])
    void oauthRefreshGrantFailsWithInvalidRefreshToken() throws Exception {
        String refreshToken = "GARBAGE"

        given()
            .param("grant_type", "refresh_token")
            .param("refresh_token", refreshToken)
        .when()
            .post(OauthRoute)
        .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("size()", is(2))
            .body("message", not(isEmptyOrNullString()))
            .body("error", is("invalid_grant"))
    }

    /** Error responses include only error and message properties
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/12">#12</a>
     * @throws Exception
     */
    @Test(groups=["v100", "json"])
    void oauthErrorsAreTransformedProperly() throws Exception {

        given()
            .param("grant_type", "password")
            .param("username", "foobar")
            .param("password", "nopenopenope!")
        .when()
            .post(OauthRoute)
        .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .header("Cache-Control", containsString("no-store"))
            .header("Pragma", is("no-cache"))
            .body("size()", is(2))
            .body("message", not(isEmptyOrNullString()))
            .body("error", not(isEmptyOrNullString()))
    }

    /**
     * We should be able to use the client_credentials grant type to get an access token
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/8">#8</a>
     */
    @Test(groups=["v100", "json"])
    void oauthClientCredentialsGrantSucceeds() throws Exception {

        def account = createTestAccount()
        rateLimitSleep() // sleep to get around rate limiting

        // Attempt to get tokens
        given()
            .param("grant_type", "client_credentials")
            .auth()
                .preemptive().basic(clientCredentialsId, clientCredentialsSecret)
            .contentType(ContentType.URLENC)
        .when()
            .post(OauthRoute)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("access_token", not(isEmptyOrNullString()))
            .body("token_type", equalToIgnoringCase("Bearer"))
            .body("expires_in", is(3600))
            .body("refresh_token", nullValue())
            .header("Cache-Control", containsString("no-store"))
            .header("Pragma", is("no-cache"))
    }

    /** We shouldn't be able to use client credentials to get an access token without a API secret
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/8">#8</a>
     */
    @Test(groups=["v100", "json"])
    void oauthClientCredentialsGrantFailsWithoutAPISecret() throws Exception {
        // Get API keys so we can use it for client credentials

        // TODO - commenting out Stormpath specific interaction
        /*
        Response apiKeysResource = given()
            .header("User-Agent", "stormpath-framework-tck")
            .header("Authorization", RestUtils.getBasicAuthorizationHeaderValue())
            .header("Content-Type", "application/json")
            .port(443)
        .when()
            .post(account.href + "/apiKeys")
        .then()
            .statusCode(201)
        .extract()
            .response()

        String apiKeyId = apiKeysResource.body().jsonPath().getString("id")
        */

        // Attempt to get tokens

        given()
            .param("grant_type", "client_credentials")
            .auth()
                .preemptive().basic("NOT_A_VALID_API_ID", "NOT_A_VALID_API_SECRET")
            .contentType(ContentType.URLENC)
        .when()
            .post(OauthRoute)
        .then()
            .statusCode(401)
            .contentType(ContentType.JSON)
            .body("error", is("invalid_client"))
    }

    private boolean isAccountSubInClaim(def account, String jwt) {
        def sub = JwtUtils.extractJwtClaim(jwt, "sub")
        return account.href == sub || account.email == sub
    }

    @Override
    protected TestAccount createTestAccount() {

        TestAccount account = new TestAccount(WITHOUT_DISPOSABLE_EMAIL, [stormpathApiKey_1: "${clientCredentialsId}:${clientCredentialsSecret}".toString()])

        account.registerOnServer()
        deleteOnClassTeardown(account.href)
        return account
    }
}
