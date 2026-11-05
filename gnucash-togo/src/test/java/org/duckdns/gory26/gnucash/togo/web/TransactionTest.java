package org.duckdns.gory26.gnucash.togo.web;

import org.duckdns.gory26.gnucash.book.SampleBook;
import org.duckdns.gory26.gnucash.togo.AppTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Edit and delete: only for transactions the app may edit (ADR-0008), even when the link is forged. */
class TransactionTest extends AppTest {

    private static final String BACK = "/accounts/" + SampleBook.CASH;

    private MockHttpSession session;

    @BeforeEach
    void logIn() throws Exception {
        session = login();
    }

    @Test
    void theEditPageStartsFromTheTransaction() throws Exception {
        String page = mvc.perform(get("/transactions/" + concert() + "/edit").param("returnTo", BACK).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(page).contains("value=\"2026-11-10\"").contains("value=\"Concert tickets (maybe?)\"")
                .contains("value=\"200.00\"").contains("value=\"Assets:Cash\"").contains("value=\"Expenses:Misc\"")
                .contains("hx-post=\"/transactions/" + concert() + "\"")
                .contains("href=\"" + BACK + "\"").contains(">Delete</button>");
    }

    @Test
    void aTransactionLeftToDesktopHasNoEditPage() throws Exception {
        String page = mvc.perform(get("/transactions/" + rewe() + "/edit").session(session))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(page).contains("This transaction is left to desktop");
    }

    @Test
    void aForgedUpdateOfATransactionLeftToDesktopIsRefused() throws Exception {
        String form = mvc.perform(withForm(post("/transactions/" + rewe()), "99.00"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(form).contains("Not saved: this transaction is left to desktop: a split is reconciled");
        assertThat(sql.sql("SELECT value_num FROM splits WHERE tx_guid = :tx AND value_num > 0").param("tx", rewe())
                .query(Long.class).single()).isEqualTo(1234);
    }

    @Test
    void aForgedDeleteOfATransactionLeftToDesktopIsRefused() throws Exception {
        String form = mvc.perform(withForm(post("/transactions/" + rewe() + "/delete"), "12.34"))
                .andReturn().getResponse().getContentAsString();

        assertThat(form).contains("Not saved: this transaction is left to desktop");
        assertThat(count(rewe())).isEqualTo(1);
    }

    @Test
    void savingAnEditGoesBackToTheRegister() throws Exception {
        String tx = concert();

        mvc.perform(withForm(post("/transactions/" + tx), "150.00"))
                .andExpect(header().string("HX-Redirect", BACK));

        assertThat(sql.sql("SELECT value_num FROM splits WHERE tx_guid = :tx AND value_num > 0").param("tx", tx)
                .query(Long.class).single()).isEqualTo(15000);
    }

    @Test
    void anEditWithABadAmountComesBackWithTheError() throws Exception {
        String form = mvc.perform(withForm(post("/transactions/" + concert()), "-5"))
                .andReturn().getResponse().getContentAsString();

        assertThat(form).contains("No sign, please").contains("value=\"-5\"");
    }

    @Test
    void deletingGoesBackToTheRegister() throws Exception {
        String tx = concert();

        mvc.perform(withForm(post("/transactions/" + tx + "/delete"), "200.00"))
                .andExpect(header().string("HX-Redirect", BACK));

        assertThat(count(tx)).isZero();
    }

    @Test
    void returnToOnlyLeadsToARegister() throws Exception {
        mvc.perform(withForm(post("/transactions/" + concert()), "200.00", "https://example.com/"))
                .andExpect(header().string("HX-Redirect", "/accounts"));
    }

    // ---------------------------------------------------------------- helpers

    private MockHttpServletRequestBuilder withForm(MockHttpServletRequestBuilder request, String amount) {
        return withForm(request, amount, BACK);
    }

    private MockHttpServletRequestBuilder withForm(MockHttpServletRequestBuilder request, String amount, String returnTo) {
        return request.session(session).with(csrf().asHeader()).header("HX-Request", "true")
                .param("date", "2026-11-10")
                .param("text", "Concert tickets")
                .param("amount", amount)
                .param("from", SampleBook.CASH)
                .param("to", SampleBook.MISC)
                .param("returnTo", returnTo);
    }

    private String concert() {
        return tx("Concert tickets (maybe?)");
    }

    /** Reconciled, so desktop's. */
    private String rewe() {
        return tx("[Tag 1] [Tag 2]  Rewe"); // two spaces, as desktop wrote it
    }

    private String tx(String description) {
        return sql.sql("SELECT guid FROM transactions WHERE description = :d").param("d", description)
                .query(String.class).single();
    }

    private int count(String tx) {
        return sql.sql("SELECT COUNT(*) FROM transactions WHERE guid = :tx").param("tx", tx).query(Integer.class).single();
    }
}
