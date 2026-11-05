package org.duckdns.gory26.gnucash.togo.session;

import org.duckdns.gory26.gnucash.togo.AppTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
