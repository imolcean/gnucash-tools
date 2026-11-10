package org.duckdns.gory26.gnucash.togo.web;

import org.duckdns.gory26.gnucash.book.SampleBook;
import org.duckdns.gory26.gnucash.togo.AppTest;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Cash register of the sample book on 2026-11-04, as the prototype (variant B) shows it. */
class RegisterTest extends AppTest {

    static final String NNBSP = " ";
    static final String NBSP = " ";

    @Test
    void theHeaderShowsThePathAndThePresentBalance() throws Exception {
        String page = cash();

        assertThat(page).containsPattern("<span class=\"muted small\">Assets:</span><span>Cash</span>");
        assertThat(page).contains("class=\"bal num\">1" + NNBSP + "585.00" + NBSP + "€<");
    }

    @Test
    void daysCarryTheirEndOfDayBalanceAndFutureDaysAreMarked() throws Exception {
        String page = cash();

        assertThat(page).contains("<div class=\"day future\">")
                .contains("Tue 10.11.2026 · future</span>")
                .contains("1" + NNBSP + "385.00" + NBSP + "€")
                .contains("Wed 04.11.2026</span>\n            <span class=\"num\">1" + NNBSP + "585.00" + NBSP + "€");
        assertThat(Pattern.compile("class=\"day").matcher(page).results().count()).isEqualTo(5);
    }

    @Test
    void rowsShowTagsTheOtherAccountAndTheSignedAmount() throws Exception {
        String page = cash();

        assertThat(page).contains("<span class=\"chip\">Tag 1</span><span class=\"chip\">Tag 2</span><span>Rewe</span>");
        assertThat(page).contains(">Groceries · Forgot something<");
        assertThat(page).contains("−200.00" + NBSP + "€").contains("+1" + NNBSP + "500.00" + NBSP + "€");
        assertThat(page).contains(">— split (4) —<");
    }

    @Test
    void onlyEditableRowsLinkToTheEditPage() throws Exception {
        String page = cash();

        assertThat(Pattern.compile("<a class=\"item[^\"]*\" href=\"/transactions/[0-9a-f]{32}/edit\\?returnTo=/accounts/"
                + SampleBook.CASH + "\">").matcher(page).results().count())
                .as("Concert tickets and Storage room").isEqualTo(2);
        assertThat(Pattern.compile("class=\"lock\" title=").matcher(page).results().count())
                .as("visible locks on the six desktop-only rows").isEqualTo(6);
    }

    @Test
    void reconciledRowsShowTheirMarker() throws Exception {
        assertThat(Pattern.compile("<span class=\"rec y\"").matcher(cash()).results().count()).isEqualTo(3);
    }

    @Test
    void theReconcileMarkerNamesItsState() throws Exception {
        assertThat(cash()).contains("<span class=\"rec y\" title=\"Reconciled\">y</span>")
                .contains("<span class=\"rec n\" title=\"Not reconciled\">n</span>");
    }

    @Test
    void anUnknownAccountIsNotFound() throws Exception {
        mvc.perform(get("/accounts/" + "0".repeat(32)).session(login())).andExpect(status().isNotFound());
    }

    @Test
    void aForgedCursorIsABadRequest() throws Exception {
        mvc.perform(get("/accounts/" + SampleBook.CASH + "/rows?after=nonsense").session(login()))
                .andExpect(status().isBadRequest());
    }

    private String cash() throws Exception {
        return mvc.perform(get("/accounts/" + SampleBook.CASH).session(login())).andReturn().getResponse().getContentAsString();
    }

    /** The "Load more" URL on a page or fragment, or null on the last page. */
    static String loadMore(String html) {
        Matcher m = Pattern.compile("hx-get=\"([^\"]+)\"").matcher(html);
        return m.find() ? m.group(1).replace("&amp;", "&") : null;
    }
}
