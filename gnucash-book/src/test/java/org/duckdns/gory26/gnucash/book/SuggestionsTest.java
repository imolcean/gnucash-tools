package org.duckdns.gory26.gnucash.book;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SuggestionsTest {

    private final GnuCashBook book = new GnuCashBook(SampleBook.freshCopy(), SampleBook.TODAY);

    @Test
    void descriptionsMatchTheTextAfterTheTagsMostRecentFirst() {
        assertThat(book.suggestDescriptions("rewe")).containsExactly("Rewe again", "Rewe");
        assertThat(book.suggestDescriptions("USD")).containsExactly("Sell USD @ 1.3", "Buy USD @ 1.2");
    }

    @Test
    void tagsDoNotMatchAsDescriptionText() {
        assertThat(book.suggestDescriptions("Tag")).isEmpty();
    }

    @Test
    void anEmptyQueryListsRecentDescriptions() {
        assertThat(book.suggestDescriptions(""))
                .hasSize(8)
                .startsWith("Concert tickets (maybe?)", "Sell USD @ 1.3")
                .endsWith("Opening Balance");
    }

    @Test
    void likeWildcardsAreLiteral() {
        assertThat(book.suggestDescriptions("%")).isEmpty();
        assertThat(book.suggestDescriptions("_")).isEmpty();
        assertThat(book.suggestDescriptions("(maybe?)")).containsExactly("Concert tickets (maybe?)");
    }

    @Test
    void quickFillTakesTagsAccountsAndAmountFromTheLatestTwoSplitMatch() {
        assertThat(book.quickFill("Rewe")).contains(new QuickFill(List.of("Tag 1", "Tag 2"), "Rewe",
                new GncNumeric(1234, 100), SampleBook.CASH, SampleBook.GROCERIES));
        assertThat(book.quickFill("Rewe again")).contains(new QuickFill(List.of("Tag 1"), "Rewe again",
                new GncNumeric(766, 100), SampleBook.CASH, SampleBook.GROCERIES));
    }

    @Test
    void quickFillSkipsMultiSplitTransactions() {
        assertThat(book.quickFill("Salary")).isEmpty();
        assertThat(book.quickFill("Nothing like this")).isEmpty();
    }

    @Test
    void tagsAreDistinctAndMatchByPrefix() {
        assertThat(book.suggestTags("")).containsExactly("Tag 1", "Tag 2");
        assertThat(book.suggestTags("tag 2")).containsExactly("Tag 2");
        assertThat(book.suggestTags("Rewe")).isEmpty();
    }

    @Test
    void accountsOfferOnlyTransferableOnesMostUsedFirst() {
        assertThat(book.suggestAccounts("")).extracting(AccountChoice::path).containsExactly(
                "Assets:Cash",                // 8 uses
                "Assets:Cash USD",            // 2 uses, last 2026-11-04
                "Expenses:Rent",              // 2 uses, last 2026-11-03
                "Expenses:Food:Groceries",    // 2 uses, last 2026-11-02
                "Expenses:Misc",              // 1 use, 2026-11-10
                "Income:Salary",              // 1 use, 2026-11-03
                "Equity:Opening Balances",    // 1 use, 2026-11-01
                "Assets:Checking",            // unused, by path
                "Liabilities:Credit Card");
    }

    @Test
    void accountsMatchEveryWordOfTheQueryInThePath() {
        assertThat(book.suggestAccounts("exp gro")).extracting(AccountChoice::path).containsExactly("Expenses:Food:Groceries");
        assertThat(book.suggestAccounts("CASH")).extracting(AccountChoice::path).containsExactly("Assets:Cash", "Assets:Cash USD");
        assertThat(book.suggestAccounts("cash usd")).singleElement()
                .isEqualTo(new AccountChoice(SampleBook.CASH_USD, "Assets:Cash USD", "USD"));
        assertThat(book.suggestAccounts("hidden")).isEmpty();
        assertThat(book.suggestAccounts("currency")).isEmpty();
    }
}
