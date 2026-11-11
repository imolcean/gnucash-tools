package org.duckdns.gory26.gnucash.togo.web;

import org.duckdns.gory26.gnucash.book.SampleBook;
import org.duckdns.gory26.gnucash.togo.AppTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The book going away under a logged-in session: a database restart, a database that is down for a while,
 * and a password changed behind the app's back.
 */
class BookOutageTest extends AppTest {

    /** Hikari hands out a connection used within this window without checking it. */
    private static final long HIKARI_ALIVE_BYPASS_MS = 500;
    /** The warnings these outages cause on purpose; elsewhere in the build they would mean something. */
    private static final List<String> EXPECTED_WARNINGS = List.of(
            "com.zaxxer.hikari.pool.PoolBase", BookFailureAdvice.class.getName());
    /** The cluster as superuser, to stop and start connections to the book. */
    private final JdbcClient cluster = JdbcClient.create(SampleBook.dataSource("postgres", "postgres", "postgres"));
    private final LoggingSystem logging = LoggingSystem.get(BookOutageTest.class.getClassLoader());

    private MockHttpSession session;

    @BeforeEach
    void logIn() throws Exception {
        EXPECTED_WARNINGS.forEach(logger -> logging.setLogLevel(logger, LogLevel.ERROR));
        session = login();
    }

    @AfterEach
    void warningsBack() {
        EXPECTED_WARNINGS.forEach(logger -> logging.setLogLevel(logger, null));
    }

    @Test
    void afterARestartTheNextRequestReconnectsWithoutANewLogin() throws Exception {
        assertThat(page("/accounts").getStatus()).isEqualTo(200);

        dropAppConnections();

        MockHttpServletResponse after = page("/accounts");
        assertThat(after.getStatus()).isEqualTo(200);
        assertThat(after.getContentAsString()).contains("585.00");
        assertThat(sessions.openCount()).isEqualTo(1);
    }

    @Test
    void aBookThatIsDownShowsAnErrorPageUntilItIsBack() throws Exception {
        bookDown();

        MockHttpServletResponse down = page("/accounts?hideZero=false");
        assertThat(down.getStatus()).isEqualTo(503);
        assertThat(down.getContentAsString()).contains("Cannot reach the book. Try again in a moment.")
                .contains("href=\"/accounts?hideZero=false\"").contains("Log out");

        cluster.sql("GRANT CONNECT ON DATABASE " + BOOK + " TO " + ROLE).update();
        assertThat(page("/accounts").getStatus()).isEqualTo(200);
    }

    /** htmx would ignore an error status, and swapping the banner over the form would lose what was typed. */
    @Test
    void aSaveWhileTheBookIsDownShowsTheBannerAndLeavesTheForm() throws Exception {
        bookDown();

        MockHttpServletResponse save = mvc.perform(post("/").session(session).with(csrf().asHeader())
                        .header("HX-Request", "true").param("date", "2026-11-03").param("text", "Lunch")
                        .param("amount", "1").param("from", SampleBook.CASH).param("to", SampleBook.GROCERIES))
                .andReturn().getResponse();

        assertThat(save.getStatus()).isEqualTo(200);
        assertThat(save.getHeader("HX-Retarget")).isEqualTo("#banners");
        assertThat(save.getHeader("HX-Reswap")).isEqualTo("outerHTML");
        assertThat(save.getContentAsString()).startsWith("<div id=\"banners\"")
                .contains("Cannot reach the book.").doesNotContain("entry-form");
    }

    @Test
    void aPasswordChangedBehindTheAppsBackLogsOut() throws Exception {
        changePassword();

        MockHttpServletResponse page = page("/accounts");

        assertThat(page.getRedirectedUrl()).isEqualTo("/login?refused");
        assertThat(sessions.openCount()).isZero();
        assertThat(mvc.perform(get("/login?refused")).andReturn().getResponse().getContentAsString())
                .contains("The book no longer accepts your login. Log in again.");
    }

    @Test
    void aPasswordChangedBehindTheAppsBackLogsOutHtmxToo() throws Exception {
        changePassword();

        MockHttpServletResponse fragment = mvc.perform(get("/suggest/tags").param("tag", "ta").session(session)
                .header("HX-Request", "true")).andReturn().getResponse();

        assertThat(fragment.getHeader("HX-Redirect")).isEqualTo("/login?refused");
        assertThat(sessions.openCount()).isZero();
        assertThat(page("/accounts").getRedirectedUrl()).isEqualTo("/login");
    }

    // ---------------------------------------------------------------- helpers

    private MockHttpServletResponse page(String path) throws Exception {
        return mvc.perform(get(path).session(session)).andReturn().getResponse();
    }

    /** What a database restart does to the app's open connections. */
    private void dropAppConnections() throws InterruptedException {
        List<Boolean> dropped = cluster.sql("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE usename = :role")
                .param("role", ROLE).query(Boolean.class).list();
        assertThat(dropped).as("the session's idle connection").isNotEmpty().containsOnly(true);
        // Past the window, so Hikari checks the dead connection instead of handing it out.
        Thread.sleep(HIKARI_ALIVE_BYPASS_MS + 100);
    }

    /**
     * No new connections to the book for the app role (the next test's fresh book grants them again); the
     * session's pool holds none yet after the login.
     */
    private void bookDown() {
        cluster.sql("REVOKE CONNECT ON DATABASE " + BOOK + " FROM PUBLIC, " + ROLE).update();
        cluster.sql("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE usename = :role")
                .param("role", ROLE).query(Boolean.class).list();
    }

    private void changePassword() throws Exception {
        assertThat(page("/accounts").getStatus()).isEqualTo(200);
        cluster.sql("ALTER ROLE " + ROLE + " PASSWORD 'changed'").update();
        dropAppConnections();
    }
}
