package org.duckdns.gory26.gnucash.togo.web;

import org.duckdns.gory26.gnucash.togo.AppTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/** ADR-0004 and ADR-0005 banners on every page. */
class BannerTest extends AppTest {

    @Test
    void aLockedBookShowsWhoHoldsIt() throws Exception {
        lockBook();
        MockHttpSession session = login();

        String page = page(session);

        assertThat(page).contains("Locked by Leopard (PID 8300)")
                .contains("Clear the lock held by Leopard (PID 8300)? Desktop will not see the phone&#39;s writes.");
    }

    @Test
    void clearLockRemovesTheBanner() throws Exception {
        lockBook();
        MockHttpSession session = login();

        mvc.perform(post("/lock/clear").session(session).with(csrf().asHeader()))
                .andExpect(header().string("HX-Refresh", "true"));

        assertThat(page(session)).doesNotContain("Locked by");
    }

    @Test
    void anUnknownSchemaMakesTheAppReadOnly() throws Exception {
        sql.sql("UPDATE versions SET table_version = 99 WHERE table_name = 'splits'").update();
        MockHttpSession session = login();

        assertThat(page(session)).contains("Unknown schema → read-only").contains("splits: version 99, expected 5");
    }

    @Test
    void aWritableBookHasNoBanners() throws Exception {
        assertThat(page(login())).doesNotContain("Locked by").doesNotContain("Unknown schema");
    }

    @Test
    void everyPageCarriesTheCsrfHeaderForHtmx() throws Exception {
        assertThat(page(login())).containsPattern("hx-headers='\\{&quot;X-CSRF-TOKEN&quot;: &quot;[^&]+&quot;}'");
    }

    private String page(MockHttpSession session) throws Exception {
        return mvc.perform(get("/").session(session)).andReturn().getResponse().getContentAsString();
    }
}
