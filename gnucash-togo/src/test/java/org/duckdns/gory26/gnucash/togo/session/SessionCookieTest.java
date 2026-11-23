package org.duckdns.gory26.gnucash.togo.session;

import org.apache.catalina.Context;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0006: a phone stays logged in when its browser closes. Needs a real Tomcat, because MockMvc neither sets
 * the session cookie nor applies the session timeout.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("docker")
class SessionCookieTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WebServerApplicationContext context;

    @Test
    void theSessionCookieOutlivesTheBrowser() throws Exception {
        // The login page stores its CSRF token in the session, so it already sets the cookie.
        HttpResponse<Void> page;
        try(HttpClient http = HttpClient.newHttpClient()) {
            page = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login")).build(),
                    HttpResponse.BodyHandlers.discarding());
        }

        assertThat(page.headers().allValues("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .startsWith("JSESSIONID=")
                        .contains("Max-Age=" + Duration.ofDays(365).toSeconds())
                        .contains("HttpOnly"));
    }

    @Test
    void anUnusedSessionEndsAfter90Days() {
        TomcatWebServer server = (TomcatWebServer) context.getWebServer();
        Context app = (Context) server.getTomcat().getHost().findChildren()[0];

        assertThat((long) app.getSessionTimeout()).isEqualTo(Duration.ofDays(90).toMinutes());
    }
}
