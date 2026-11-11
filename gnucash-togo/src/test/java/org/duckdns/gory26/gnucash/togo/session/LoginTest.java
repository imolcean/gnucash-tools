package org.duckdns.gory26.gnucash.togo.session;

import org.duckdns.gory26.gnucash.togo.AppTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADR-0006: passthrough login, one pool per session, closed when the session ends. */
@ExtendWith(OutputCaptureExtension.class)
class LoginTest extends AppTest {

    @Autowired
    private ApplicationEventPublisher events;

    @Test
    void theRightPasswordLogsInAndOpensOnePool() throws Exception {
        mvc.perform(formLogin("/login").user(ROLE).password(PASSWORD))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername(ROLE));

        assertThat(sessions.openCount()).isEqualTo(1);
    }

    @Test
    void aWrongPasswordIsRefusedWithNoPoolLeftOpen() throws Exception {
        mvc.perform(formLogin("/login").user(ROLE).password("wrong"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());

        assertThat(sessions.openCount()).isZero();
    }

    @Test
    void aLoginLogsOneLineWithTheDeviceAndPool(CapturedOutput output) throws Exception {
        // The shared logback-test.xml shows WARN and up; this line is INFO, as it is in production.
        LoggingSystem logging = LoggingSystem.get(getClass().getClassLoader());
        String logger = BookAuthenticationProvider.class.getName();
        logging.setLogLevel(logger, LogLevel.INFO);
        try {
            mvc.perform(formLogin("/login").user(ROLE).password(PASSWORD)).andExpect(authenticated());
        }
        finally {
            logging.setLogLevel(logger, null);
        }

        assertThat(output.getOut()).containsPattern("Login ok: " + ROLE + " from 127\\.0\\.0\\.1, pool book-\\d+");
    }

    @Test
    void aWrongPasswordLogsOneLineWithoutAStackTrace(CapturedOutput output) throws Exception {
        mvc.perform(formLogin("/login").user(ROLE).password("wrong")).andExpect(unauthenticated());

        assertThat(output.getOut()).contains("Login refused: " + ROLE + " from 127.0.0.1")
                .doesNotContain("PSQLException").doesNotContain("\tat ");
    }

    @Test
    void aWrongPasswordSaysSo() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(formLogin("/login").user(ROLE).password("wrong"))
                .andReturn().getRequest().getSession();

        String page = mvc.perform(get("/login?error").session(session)).andReturn().getResponse().getContentAsString();

        assertThat(page).contains("Wrong user or password.");
    }

    @Test
    void anUnreachableBookSaysSoWithoutTheRawError() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(WebAttributes.AUTHENTICATION_EXCEPTION, new AuthenticationServiceException(
                "Cannot reach the book", new IllegalStateException("Connection to db.internal:5432 refused")));

        String page = mvc.perform(get("/login?error").session(session)).andReturn().getResponse().getContentAsString();

        assertThat(page).contains("Cannot reach the book.").doesNotContain("db.internal");
    }

    @Test
    void theLoginPageCarriesTheAppHeaderWithoutTheMenu() throws Exception {
        String page = mvc.perform(get("/login")).andReturn().getResponse().getContentAsString();

        assertThat(page).contains("<nav class=\"app\">").contains("lucide.svg#tree-palm").contains(">Togo</span>")
                .doesNotContain(">Accounts</a>").doesNotContain(">Log out</button>");
    }

    @Test
    void pagesNeedALogin() throws Exception {
        mvc.perform(get("/accounts")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void logoutClosesThePool() throws Exception {
        MockHttpSession session = login();

        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));

        assertThat(sessions.openCount()).isZero();
    }

    @Test
    void aDestroyedSessionClosesThePool() throws Exception {
        MockHttpSession session = login();

        events.publishEvent(new HttpSessionDestroyedEvent(session));

        assertThat(sessions.openCount()).isZero();
    }

    @Test
    void aPostWithoutTheCsrfTokenIsRejected() throws Exception {
        MockHttpSession session = login();
        lockBook();

        mvc.perform(post("/lock/clear").session(session)).andExpect(status().isForbidden());

        assertThat(sql.sql("SELECT COUNT(*) FROM gnclock").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void theCsrfHeaderIsAccepted() throws Exception {
        MockHttpSession session = login();
        lockBook();

        mvc.perform(post("/lock/clear").session(session).with(csrf().asHeader())).andExpect(status().isNoContent());

        assertThat(sql.sql("SELECT COUNT(*) FROM gnclock").query(Integer.class).single()).isZero();
    }
}
