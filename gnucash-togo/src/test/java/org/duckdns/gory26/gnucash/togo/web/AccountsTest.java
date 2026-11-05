package org.duckdns.gory26.gnucash.togo.web;

import org.duckdns.gory26.gnucash.book.SampleBook;
import org.duckdns.gory26.gnucash.togo.AppTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** The tree shows desktop's Present column (ADR-0008) on 2026-11-04. */
class AccountsTest extends AppTest {

    private static final String NNBSP = " ";
    private static final String NBSP = " ";

    @Test
    void showsThePresentBalancesDesktopShows() throws Exception {
        String page = accounts(login(), "?hideZero=false");

        assertThat(line(page, "Assets:Cash")).contains("1" + NNBSP + "585.00" + NBSP + "€");
        assertThat(line(page, "Assets:Cash USD")).contains("0.00" + NBSP + "$");
        assertThat(line(page, "Expenses:Food:Groceries")).contains("20.00" + NBSP + "€");
        assertThat(line(page, "Expenses:Rent")).contains("520.00" + NBSP + "€");
        assertThat(line(page, "Expenses:Misc")).contains("0.00" + NBSP + "€");
        assertThat(line(page, "Income:Salary")).contains("2" + NNBSP + "000.00" + NBSP + "€");
        assertThat(line(page, "Equity:Opening Balances")).contains("100.00" + NBSP + "€");
        assertThat(line(page, "Trading:CURRENCY:EUR")).contains("−25.00" + NBSP + "€");
        assertThat(line(page, "Assets")).contains("1" + NNBSP + "585.00" + NBSP + "€");
    }

    @Test
    void tradingStartsCollapsedAndEverythingElseOpen() throws Exception {
        String page = accounts(login(), "");

        assertThat(page).contains("<details data-path=\"Trading\">");
        assertThat(page).contains("<details data-path=\"Assets\" open=\"open\">");
    }

    @Test
    void hiddenAccountsStayOut() throws Exception {
        String page = accounts(login(), "?hideZero=false");

        assertThat(page).doesNotContain("Hidden").doesNotContain("Visible nested");
    }

    @Test
    void placeholdersExpandAndOtherAccountsOpenTheirRegister() throws Exception {
        String page = accounts(login(), "?hideZero=false");

        assertThat(page).doesNotContain("/accounts/" + SampleBook.FOOD);
        assertThat(line(page, "Expenses:Food")).contains("class=\"placeholder-name\"");
        assertThat(page).contains("href=\"/accounts/" + SampleBook.CASH + "\"");
    }

    @Test
    void zeroBalancesAreHiddenByDefaultAndTheChoiceIsRemembered() throws Exception {
        MockHttpSession session = login();

        assertThat(accounts(session, "")).doesNotContain("data-path=\"Expenses:Misc\"").contains("Show zero balances");

        assertThat(accounts(session, "?hideZero=false")).contains("data-path=\"Expenses:Misc\"");
        assertThat(accounts(session, "")).contains("data-path=\"Expenses:Misc\"").contains("Hide zero balances");
    }

    @Test
    void hidingZeroKeepsParentsOfAccountsWithMoney() throws Exception {
        String page = accounts(login(), "");

        assertThat(page).contains("data-path=\"Expenses:Food:Groceries\"").contains("data-path=\"Expenses:Food\"");
    }

    private String accounts(MockHttpSession session, String query) throws Exception {
        return mvc.perform(get("/accounts" + query).session(session)).andReturn().getResponse().getContentAsString();
    }

    /** The account's own line: from its data-path attribute up to its children or the next account. */
    private static String line(String page, String path) {
        Matcher m = Pattern.compile("data-path=\"" + Pattern.quote(path) + "\"[^>]*>(.*?)(?:data-path=|class=\"kids\"|</main>)",
                Pattern.DOTALL).matcher(page);
        assertThat(m.find()).as("account %s on the page", path).isTrue();
        return m.group(1);
    }
}
