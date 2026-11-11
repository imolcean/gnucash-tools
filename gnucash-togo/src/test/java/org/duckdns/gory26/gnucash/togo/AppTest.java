package org.duckdns.gory26.gnucash.togo;

import org.duckdns.gory26.gnucash.book.SampleBook;
import org.duckdns.gory26.gnucash.togo.session.Sessions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;

/**
 * The app against a fresh copy of the sample book per test, logged in as a role with exactly the ADR-0006
 * grants. One Spring context for all subclasses: the database name stays {@value #BOOK}, its content is
 * recreated before each test. "Today" is {@link SampleBook#TODAY}; the desktop lock is cleared unless a test
 * puts it back with {@link #lockBook()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClock.class)
public abstract class AppTest {

    protected static final String BOOK = "app_book";
    protected static final String ROLE = "togo";
    protected static final String PASSWORD = "togo";
    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected Sessions sessions;
    /** The sample book as its owner sees it, for setting up and checking state behind the app's back. */
    protected JdbcClient sql;

    @DynamicPropertySource
    static void book(DynamicPropertyRegistry registry) {
        registry.add("togo.db.host", SampleBook::host);
        registry.add("togo.db.port", SampleBook::port);
        registry.add("togo.db.name", () -> BOOK);
        // Outage tests wait this long per request; a local connection takes far less.
        registry.add("togo.db.connection-timeout", () -> "1s");
    }

    @BeforeEach
    @SuppressWarnings("SqlWithoutWhere")
        // Always single row
    void freshBook() {
        SampleBook.recreate(BOOK);
        SampleBook.grantAppRole(BOOK, ROLE, PASSWORD);
        sql = JdbcClient.create(SampleBook.dataSource(BOOK, "postgres", "postgres"));
        sql.sql("DELETE FROM gnclock").update();
    }

    @AfterEach
    void closeSessions() {
        sessions.closeAll();
    }

    /** Puts back the lock row the dump was taken with. */
    protected void lockBook() {
        sql.sql("INSERT INTO gnclock (hostname, pid) VALUES ('Leopard', 8300)").update();
    }

    /** Logs in as the app role and returns the authenticated session. */
    protected MockHttpSession login() throws Exception {
        MvcResult result = mvc.perform(formLogin("/login").user(ROLE).password(PASSWORD)).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if(session == null || result.getResponse().getRedirectedUrl() == null
                || result.getResponse().getRedirectedUrl().contains("error")) {
            throw new IllegalStateException("login failed: " + result.getResponse().getRedirectedUrl());
        }
        return session;
    }
}
