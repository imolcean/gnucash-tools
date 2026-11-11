package org.duckdns.gory26.gnucash.togo.web;

import org.duckdns.gory26.gnucash.book.GncNumeric;
import org.duckdns.gory26.gnucash.book.GnuCashBook;
import org.duckdns.gory26.gnucash.book.SampleBook;
import org.duckdns.gory26.gnucash.book.TransferDraft;
import org.duckdns.gory26.gnucash.togo.AppTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** The entry form at {@code /}: save, field errors, refusals, and the htmx pickers behind it. */
class EntryTest extends AppTest {

    private static final String NBSP = " ";

    private MockHttpSession session;

    private static void assertEveryHtmxElementInTheFormSetsItsSwap(String page, int atLeast) {
        String form = page.substring(page.indexOf("<form id=\"entry-form\""), page.lastIndexOf("</form>"));
        Matcher tags = Pattern.compile("<[a-z]+\\s[^>]*hx-(?:get|post)=[^>]*>").matcher(form);
        assertThat(tags.find()).as("the form itself").isTrue();
        int checked = 0;
        while(tags.find()) {
            assertThat(tags.group()).as("element inside the form").contains("hx-swap=");
            checked++;
        }
        assertThat(checked).isGreaterThanOrEqualTo(atLeast);
    }

    @BeforeEach
    void logIn() throws Exception {
        session = login();
    }

    @Test
    void aSavedEntryReadsBackThroughTheBook() throws Exception {
        String form = save(lunch());

        assertThat(form).contains("Saved: [Tag 1] [Tag 2] Lunch · 12.34" + NBSP + "€ · Cash → Groceries");
        String tx = sql.sql("SELECT guid FROM transactions WHERE description = '[Tag 1] [Tag 2] Lunch'")
                .query(String.class).single();
        TransferDraft saved = book().draft(tx).orElseThrow();
        assertThat(saved.date()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(saved.tags()).containsExactly("Tag 1", "Tag 2");
        assertThat(saved.amount().compareTo(new GncNumeric(1234, 100))).isZero();
        assertThat(saved.fromAccountGuid()).isEqualTo(SampleBook.CASH);
        assertThat(saved.toAccountGuid()).isEqualTo(SampleBook.GROCERIES);
        assertThat(saved.notes()).isEqualTo("with Bob");
    }

    @Test
    void afterASaveOnlyTheDateStays() throws Exception {
        String form = save(lunch());

        assertThat(form).contains("name=\"date\" id=\"date\" required value=\"2026-11-03\"")
                .doesNotContain("value=\"Lunch\"").doesNotContain("value=\"12,34\"").doesNotContain("name=\"tags\"")
                .contains("name=\"from\" value=\"\"");
    }

    @Test
    void textLeftInTheTagBoxIsSavedAsATag() throws Exception {
        save(lunch("tag", "Tag 3"));

        assertThat(sql.sql("SELECT COUNT(*) FROM transactions WHERE description = '[Tag 1] [Tag 2] [Tag 3] Lunch'")
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void aLockedBookRefusesAndKeepsTheInput() throws Exception {
        lockBook();

        String form = save(lunch());

        assertThat(form).contains("Not saved: Locked by Leopard (PID 8300)")
                .contains("value=\"Lunch\"").contains("value=\"12,34\"").contains("value=\"Tag 1\"");
        assertThat(sql.sql("SELECT COUNT(*) FROM transactions WHERE description LIKE '%Lunch'").query(Integer.class).single())
                .isZero();
    }

    @Test
    void aLockedBookDisablesSave() throws Exception {
        lockBook();

        String page = mvc.perform(get("/").session(session)).andReturn().getResponse().getContentAsString();

        assertThat(page).contains("<button type=\"submit\" disabled=\"disabled\">Save</button>");
    }

    @Test
    void aBadAmountIsAFieldErrorAndKeepsWhatWasTyped() throws Exception {
        String form = save(lunch("amount", "1.234"));

        assertThat(form).contains("Thousands or decimals? Write 1234 or 1.23.")
                .containsPattern("name=\"amount\"[^>]*value=\"1[.]234\"[^>]*aria-invalid=\"true\"")
                .contains("value=\"Lunch\"");
    }

    @Test
    void anAmountFinerThanTheCurrencyIsRefusedNotRounded() throws Exception {
        assertThat(save(lunch("amount", "12.3456"))).contains("Too many decimals for this currency.");
    }

    @Test
    void aMissingAccountIsAFieldError() throws Exception {
        String form = save(lunch("to", ""));

        assertThat(form).contains("Pick an account from the list.").doesNotContain("Saved:");
    }

    @Test
    void fromAndToMustDiffer() throws Exception {
        assertThat(save(lunch("to", SampleBook.CASH))).contains("From and to must be different accounts.");
    }

    @Test
    void anAccountOfAnotherCurrencyIsRefusedByTheBook() throws Exception {
        assertThat(save(lunch("to", SampleBook.CASH_USD))).contains("Not saved: from is in EUR but to is in USD");
    }

    @Test
    void descriptionsAreSuggestedAndPickingOneQuickFills() throws Exception {
        String suggestions = fragment(get("/suggest/descriptions").param("text", "rew"));
        assertThat(suggestions).contains("value=\"Rewe\"").contains("value=\"Rewe again\"");

        String form = fragment(post("/entry/quickfill").param("pick", "Rewe").param("date", "2026-11-04")
                .param("notes", "kept"));

        assertThat(form).contains("value=\"Rewe\"").contains("value=\"Tag 1\"").contains("value=\"Tag 2\"")
                .contains("value=\"12.34\"")
                .contains("name=\"from\" value=\"" + SampleBook.CASH + "\"")
                .contains("value=\"Expenses:Food:Groceries\"")
                .contains("value=\"2026-11-04\"").contains(">kept</textarea>");
    }

    @Test
    void labelsCarryTheirIconFromTheSprite() throws Exception {
        assertThat(page("/")).contains("<use href=\"/icons/lucide.svg#calendar\"></use>")
                .contains("<use href=\"/icons/lucide.svg#tree-palm\"></use>");
    }

    /** entry-form.js keeps the decimal point this many digits from the right: typing 1, 2, 3 shows 1.23. */
    @Test
    void theAmountIsTypedInTheFromAccountsSmallestUnit() throws Exception {
        String form = fragment(post("/entry/pick").param("pick", "from:" + SampleBook.CASH));

        assertThat(form).containsPattern("name=\"amount\"[^>]*inputmode=\"numeric\"[^>]*placeholder=\"0.00\"[^>]*data-decimals=\"2\"");
    }

    @Test
    void anAmountCanBeASumAsOnDesktop() throws Exception {
        assertThat(save(lunch("amount", "4.50 × 2 + 1.25"))).contains("Saved: [Tag 1] [Tag 2] Lunch · 10.25" + NBSP + "€");
    }

    @Test
    void aDivisionByZeroIsAFieldErrorAndKeepsWhatWasTyped() throws Exception {
        assertThat(save(lunch("amount", "12.00 ÷ 0"))).contains("Cannot divide by zero.").contains("value=\"12.00 ÷ 0\"");
    }

    @Test
    void aSumBelowZeroIsRefused() throws Exception {
        assertThat(save(lunch("amount", "1.00 − 2.00"))).contains("The amount must be more than zero.");
    }

    @Test
    void theResultLineShowsTheRoundedResultWhileTyping() throws Exception {
        String line = fragment(get("/entry/amount").param("amount", "10.00 ÷ 3").param("from", SampleBook.CASH));

        assertThat(line).contains("= 3.33" + NBSP + "€").doesNotContain("hx-swap-oob");
        assertThat(fragment(get("/entry/amount").param("amount", "12.30").param("from", SampleBook.CASH)))
                .contains("<output id=\"amount-result\" for=\"amount\"></output>");
        assertThat(fragment(get("/entry/amount").param("amount", "1 ÷ 0").param("from", SampleBook.CASH)))
                .contains("class=\"error\"").contains("Cannot divide by zero.");
    }

    @Test
    void leavingTheFieldPutsTheResultInIt() throws Exception {
        String line = fragment(get("/entry/amount").param("amount", "12.30 + 4.50").param("from", SampleBook.CASH)
                .param("done", "true"));

        assertThat(line).contains("<output id=\"amount-result\" for=\"amount\"></output>")
                .containsPattern("<input id=\"amount\"[^>]*value=\"16.80\"[^>]*hx-swap-oob=\"true\"");
    }

    /** From, to and amount come before the description, so quick-fill keeps what is already there. */
    @Test
    void quickFillFillsOnlyWhatIsStillEmpty() throws Exception {
        String form = fragment(post("/entry/quickfill").param("pick", "Rewe").param("date", "2026-11-04")
                .param("from", SampleBook.CHECKING).param("amount", "5").param("tags", "Mine"));

        assertThat(form).contains("value=\"Rewe\"")
                .contains("name=\"from\" value=\"" + SampleBook.CHECKING + "\"")
                .contains("name=\"to\" value=\"" + SampleBook.GROCERIES + "\"")
                .contains("value=\"5\"").doesNotContain("value=\"12.34\"")
                .contains("value=\"Mine\"").doesNotContain("value=\"Tag 1\"");
    }

    @Test
    void tagsAreSuggestedWithoutTheChipsAlreadyChosen() throws Exception {
        assertThat(fragment(get("/suggest/tags").param("tag", "ta"))).contains("data-tag=\"Tag 1\"").contains("data-tag=\"Tag 2\"");
        assertThat(fragment(get("/suggest/tags").param("tag", "ta").param("tags", "Tag 1")))
                .doesNotContain("data-tag=\"Tag 1\"").contains("data-tag=\"Tag 2\"");
    }

    @Test
    void accountsMatchWordsAnywhereInThePath() throws Exception {
        String suggestions = fragment(get("/suggest/accounts").param("field", "to").param("toLabel", "food groc"));

        assertThat(suggestions).contains("value=\"to:" + SampleBook.GROCERIES + "\"").contains("Expenses:Food:Groceries")
                .doesNotContain("Assets:Cash");
    }

    @Test
    void anEmptyPickerListsRecentAccountsButNoPlaceholdersOrTrading() throws Exception {
        String suggestions = fragment(get("/suggest/accounts").param("field", "from").param("fromLabel", ""));

        assertThat(suggestions).contains("Assets:Cash").doesNotContain(">Expenses:Food<").doesNotContain("Trading");
    }

    /** The list opens over the fields below it with no scroll bar, so it holds only what fits on a phone. */
    @Test
    void aSuggestionListHoldsFourItems() throws Exception {
        String suggestions = fragment(get("/suggest/accounts").param("field", "from").param("fromLabel", ""));

        assertThat(Pattern.compile("<li>").matcher(suggestions).results().count()).isEqualTo(4);
    }

    @Test
    void typingAfterAPickUnpicksTheAccount() throws Exception {
        String typing = fragment(get("/suggest/accounts").param("field", "from")
                .param("fromLabel", "Rent").param("from", SampleBook.CASH));
        String unchanged = fragment(get("/suggest/accounts").param("field", "from")
                .param("fromLabel", "Assets:Cash").param("from", SampleBook.CASH));

        assertThat(typing).contains("id=\"from-guid\" name=\"from\" value=\"\" hx-swap-oob=\"true\"");
        assertThat(unchanged).doesNotContain("hx-swap-oob");
    }

    /** entry-form.js finds the pickers by the marker; htmx refocuses the box after a pick by its id. */
    @Test
    void accountBoxesCarryAnIdAndThePickerItsMarker() throws Exception {
        assertThat(page("/")).containsPattern("class=\"field picker\" data-account-picker[^>]*>")
                .contains("<input id=\"from-label\" name=\"fromLabel\"")
                .contains("<input id=\"to-label\" name=\"toLabel\"");
    }

    @Test
    void enterTakesTheFirstAccountTheListOffers() throws Exception {
        String form = fragment(post("/entry/pick").param("first", "to").param("toLabel", "food groc"));

        assertThat(form).contains("name=\"to\" value=\"" + SampleBook.GROCERIES + "\"")
                .contains("value=\"Expenses:Food:Groceries\"");
    }

    @Test
    void enterOnTextNoAccountMatchesEmptiesTheBox() throws Exception {
        String form = fragment(post("/entry/pick").param("first", "from").param("fromLabel", "zzz"));

        assertThat(form).contains("id=\"from-guid\" name=\"from\" value=\"\"").doesNotContain("value=\"zzz\"");
    }

    @Test
    void pickingAnAccountFillsItsPicker() throws Exception {
        String form = fragment(post("/entry/pick").param("pick", "to:" + SampleBook.GROCERIES).param("toLabel", "groc"));

        assertThat(form).contains("name=\"to\" value=\"" + SampleBook.GROCERIES + "\"")
                .contains("value=\"Expenses:Food:Groceries\"");
    }

    /**
     * The form swaps itself with outerHTML, and htmx children inherit hx-swap: an element that doesn't set its own
     * would replace its target instead of filling it (a closing suggestion list once removed itself this way).
     */
    @Test
    void everyHtmxElementInsideTheFormSetsItsOwnSwap() throws Exception {
        assertEveryHtmxElementInTheFormSetsItsSwap(page("/"), 8);
    }

    /** The edit page adds the Delete button to the same form. */
    @Test
    void everyHtmxElementInsideTheEditFormSetsItsOwnSwap() throws Exception {
        save(lunch());
        String tx = sql.sql("SELECT guid FROM transactions WHERE description = '[Tag 1] [Tag 2] Lunch'")
                .query(String.class).single();

        String page = page("/transactions/" + tx + "/edit");

        assertThat(page).contains("/transactions/" + tx + "/delete");
        assertEveryHtmxElementInTheFormSetsItsSwap(page, 9);
    }

    @Test
    void todayIsTheBooksToday() throws Exception {
        assertThat(fragment(get("/entry/today"))).contains("value=\"2026-11-04\"");
    }

    // ---------------------------------------------------------------- helpers

    /** Lunch for 12,34 from Cash to Groceries on 03.11.2026, with {@code overrides} as name/value pairs. */
    private MockHttpServletRequestBuilder lunch(String... overrides) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("date", "2026-11-03");
        fields.put("text", "Lunch");
        fields.put("amount", "12,34");
        fields.put("from", SampleBook.CASH);
        fields.put("fromLabel", "Assets:Cash");
        fields.put("to", SampleBook.GROCERIES);
        fields.put("toLabel", "Expenses:Food:Groceries");
        fields.put("notes", "with Bob");
        for(int i = 0; i < overrides.length; i += 2) {
            fields.put(overrides[i], overrides[i + 1]);
        }
        MockHttpServletRequestBuilder request = post("/").param("tags", "Tag 1", "Tag 2");
        fields.forEach(request::param);
        return request;
    }

    private String page(String path) throws Exception {
        return mvc.perform(get(path).session(session)).andReturn().getResponse().getContentAsString();
    }

    private String save(MockHttpServletRequestBuilder request) throws Exception {
        return fragment(request);
    }

    private String fragment(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.session(session).with(csrf().asHeader()).header("HX-Request", "true"))
                .andReturn().getResponse().getContentAsString();
    }

    private GnuCashBook book() {
        return new GnuCashBook(SampleBook.dataSource(BOOK, "postgres", "postgres"), SampleBook.TODAY);
    }
}
