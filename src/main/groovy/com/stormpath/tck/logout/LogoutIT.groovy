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
package com.stormpath.tck.logout

import com.jayway.restassured.http.ContentType
import com.jayway.restassured.response.Cookies
import com.stormpath.tck.AbstractIT
import com.stormpath.tck.util.TestAccount
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test

import static com.jayway.restassured.RestAssured.delete
import static com.jayway.restassured.RestAssured.get
import static com.jayway.restassured.RestAssured.given
import static com.jayway.restassured.RestAssured.put
import static com.stormpath.tck.util.CookieUtils.isCookieDeleted
import static com.stormpath.tck.util.FrameworkConstants.LogoutRoute
import static com.stormpath.tck.util.Matchers.urlMatchesPath
import static com.stormpath.tck.util.TestAccount.Mode.WITHOUT_DISPOSABLE_EMAIL
import static org.hamcrest.Matchers.allOf
import static org.hamcrest.Matchers.not
import static org.testng.Assert.assertTrue

class LogoutIT extends AbstractIT {

    private void assertCookiesAreDeleted(Cookies cookies) throws Exception {
        assertTrue(isCookieDeleted(cookies.get("access_token")))
        assertTrue(isCookieDeleted(cookies.get("refresh_token")))
    }

    // TODO - this is stormpath specific and as such is commented out for now
    // An agnostic approach here might be to login with the access token and confirm that it fails
    // and try to use the refresh token and confirm that it fails
    private void assertTokenIsRevoked(String tokenJwt, String resourceType) throws Exception {
//        String tokenId = JwtUtils.extractJwtClaim(tokenJwt, "jti")
//
//        given()
//            .header("Authorization", RestUtils.getBasicAuthorizationHeaderValue())
//            .header("User-Agent", "stormpath-framework-tck")
//            .port(443)
//        .when()
//            .get(EnvUtils.stormpathBaseUrl + '/' + resourceType + '/' + tokenId)
//        .then()
//            .statusCode(404)
    }

    /** Only handle POST
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/54">#54</a>
     */
    @Test(groups=["v100", "json", "html"])
    void logoutDoesNotHandleGet() throws Exception {
        get(LogoutRoute)
            .then()
                .statusCode(allOf(not(200), not(500)))
    }

    /** Only handle POST
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/54">#54</a>
     */
    @Test(groups=["v100", "json", "html"])
    void logoutDoesNotHandlePut() throws Exception {
        put(LogoutRoute)
            .then()
                .assertThat().statusCode(allOf(not(200), not(500)))
    }

    /** Only handle POST
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/54">#54</a>
     */
    @Test(groups=["v100", "json", "html"])
    void logoutDoesNotHandleDelete() throws Exception {
        delete(LogoutRoute)
            .then()
                .assertThat().statusCode(allOf(not(200), not(500)))
    }

    /** Return 200 OK for unauthenticated JSON request
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/172">#172</a>
     * @throws Exception
     */
    @Test(groups=["v100", "json"])
    void logoutSucceedsOnUnauthenticatedJsonRequest() throws Exception {

        given()
            .accept(ContentType.JSON)
        .when()
            .post(LogoutRoute)
        .then()
            .statusCode(200)
    }

    /** Delete cookies on JSON logout
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/174">#174</a>
     * @throws Exception
     */
    @Test(groups=["v100", "json"])
    void logoutDeletesCookiesJson() throws Exception {

        def account = createTestAccount()
        def sessionCookies = createSession(account)

        Cookies detailedCookies =
            given()
                .accept(ContentType.JSON)
                .cookies(sessionCookies)
            .when()
                .post(LogoutRoute)
            .then()
            .extract()
                .detailedCookies()

        assertCookiesAreDeleted(detailedCookies)
    }

    /** Return 200 OK for successful JSON logout
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/171">#171</a>
     * @throws Exception
     */
    @Test(groups=["v100", "json"])
    void logoutReturns200OKOnSuccess() throws Exception {

        def account = createTestAccount()
        def sessionCookies = createSession(account)

        given()
            .accept(ContentType.JSON)
            .cookies(sessionCookies)
        .when()
            .post(LogoutRoute)
        .then()
            .statusCode(200)
    }

    /** Revoke tokens on Stormpath for JSON request
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/175">#175</a>
     * @throws Exception
     */
    @Test(groups=["v100", "json"])
    void logoutRevokesTokensInCookiesAfterSuccessJson() throws Exception {

        def account = createTestAccount()
        def sessionCookies = createSession(account)

        given()
            .accept(ContentType.JSON)
            .cookies(sessionCookies)
        .when()
            .post(LogoutRoute)
        .then()
            .statusCode(200)

        assertTokenIsRevoked(sessionCookies.get("access_token"), "accessTokens")
        assertTokenIsRevoked(sessionCookies.get("refresh_token"), "refreshTokens")
    }

    @Test(groups=["v100", "json"])
    void logoutRevokesTokensInHeadersAfterSuccessJson() throws Exception {
        Tuple2 tokens = createTestAccountTokens()

        given()
            .accept(ContentType.JSON)
            .header("Authorization", "Bearer " + tokens.first.toString())
        .when()
            .post(LogoutRoute)
        .then()
            .statusCode(200)

        assertTokenIsRevoked(tokens.first.toString(), "accessTokens")
        assertTokenIsRevoked(tokens.second.toString(), "refreshTokens")
    }

    /** Redirect to nextUri for unauthenticated request
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/173">#173</a>
     * @throws Exception
     */
    @Test(groups=["v100", "html"])
    void logoutRedirectsToNextUriOnUnauthenticatedRequest() throws Exception {

        given()
            .accept(ContentType.HTML)
        .when()
            .post(LogoutRoute)
        .then()
            .statusCode(302)
            .header("Location", urlMatchesPath("/"))
    }

    /** Redirect to nextUri on successful logout
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/170">#170</a>
     * @throws Exception
     */
    @Test(groups=["v100", "html"])
    void logoutRedirectsToNextUriOnSuccess() throws Exception {

        def account = createTestAccount()
        def sessionCookies = createSession(account)

        given()
            .accept(ContentType.HTML)
            .cookies(sessionCookies)
        .when()
            .post(LogoutRoute)
        .then()
            .statusCode(302)
            .header("Location", urlMatchesPath("/"))
    }

    /** Delete cookies on logout
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/53">#53</a>
     * @throws Exception
     */
    @Test(groups=["v100", "html"])
    void logoutDeletesCookiesHtml() throws Exception {

        def account = createTestAccount()
        def sessionCookies = createSession(account)

        Cookies detailedCookies =
                given()
                    .accept(ContentType.HTML)
                    .cookies(sessionCookies)
                .when()
                    .post(LogoutRoute)
                .then()
                .extract()
                    .detailedCookies()

        assertCookiesAreDeleted(detailedCookies)
    }

    /** Revoke Stormpath tokens on logout
     * @see <a href="https://github.com/stormpath/stormpath-framework-tck/issues/169">#169</a>
     * @throws Exception
     */
    @Test(groups=["v100", "html"])
    void logoutRevokesTokensHtml() throws Exception {

        def account = createTestAccount()
        def sessionCookies = createSession(account)

        given()
            .accept(ContentType.HTML)
            .cookies(sessionCookies)
        .post(LogoutRoute)

        assertTokenIsRevoked(sessionCookies.get("access_token"), "accessTokens")
        assertTokenIsRevoked(sessionCookies.get("refresh_token"), "refreshTokens")
    }
}
