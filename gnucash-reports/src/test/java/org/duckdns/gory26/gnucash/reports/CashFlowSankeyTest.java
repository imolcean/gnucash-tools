package org.duckdns.gory26.gnucash.reports;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Cash Flow Sankey against GnuCash's own Cash Flow report, both run by GnuCash 5.16 on the same book for
 * November 2026 in EUR, with the default accounts (Cash, Cash USD, Checking and the other asset accounts).
 * The amounts written out here were worked out by hand from the books' rows.
 * <p>
 * "Include Trading Accounts in report" does what it says in the Sankey (default off: trading flows hidden), but
 * the opposite in the standard report (ADR-0011). So the Sankey without trading flows is compared with the
 * standard report with its option ON, and the Sankey with trading flows with the standard report's default.
 */
class CashFlowSankeyTest {

    private static final String SAMPLE = Books.sample();
    private static final String RICH = Books.rich();

    /** Default options: trading flows hidden. */
    private static final String SANKEY = "Cash Flow Sankey November";
    private static final String SANKEY_WITH_TRADING = SANKEY + " with trading";
    /** Default options, which in the shipped report count the trading splits. */
    private static final String CASH_FLOW_WITH_TRADING = "Cash Flow November";
    /** The shipped report's option switched ON, which leaves the trading splits out. */
    private static final String CASH_FLOW_WITHOUT_TRADING = "Cash Flow November without trading";
    private static final String DOT = GnuCash.SEPARATOR;

    private static final String CASH = "Assets" + DOT + "Cash";
    private static final String CASH_USD = "Assets" + DOT + "Cash USD";
    private static final String CHECKING = "Assets" + DOT + "Checking";
    private static final String SALARY = "Income" + DOT + "Salary";
    private static final String FOOD = "Expenses" + DOT + "Food";
    private static final String TRADING = "Trading" + DOT + "CURRENCY";

    @Test
    void byDefaultTheTotalsAreTheCashFlowReportsWithoutTradingSplits() {
        Report sankey = GnuCash.report(SANKEY, SAMPLE);
        Report cashFlow = GnuCash.report(CASH_FLOW_WITHOUT_TRADING, SAMPLE);

        assertThat(sankey.data("money-in")).isEqualByComparingTo("2100.00")
                .isEqualByComparingTo(cashFlow.amountAfter("Money In"));
        assertThat(sankey.data("money-out")).isEqualByComparingTo("740.00")
                .isEqualByComparingTo(cashFlow.amountAfter("Money Out"));
        assertThat(sankey.data("difference")).isEqualByComparingTo("1360.00")
                .isEqualByComparingTo(cashFlow.amountAfter("Difference"));
        // the same totals as text below the diagram
        assertThat(sankey.amountAfter("Money In")).isEqualByComparingTo("2100.00");
        assertThat(sankey.amountAfter("Money Out")).isEqualByComparingTo("740.00");
        assertThat(sankey.amountAfter("Difference")).isEqualByComparingTo("1360.00");
        // the currency exchange is a transfer between two selected accounts once its trading splits are left out
        assertThat(sankey.hasNode(TRADING)).isFalse();
        assertThat(sankey.hasNode(CASH_USD)).isFalse();
    }

    @Test
    void withTradingAccountsSwitchedOnTheTotalsAreTheCashFlowReportsDefault() {
        Report sankey = GnuCash.report(SANKEY_WITH_TRADING, SAMPLE);
        Report cashFlow = GnuCash.report(CASH_FLOW_WITH_TRADING, SAMPLE);

        assertThat(sankey.data("money-in")).isEqualByComparingTo("2725.00")
                .isEqualByComparingTo(cashFlow.amountAfter("Money In"));
        assertThat(sankey.data("money-out")).isEqualByComparingTo("1365.00")
                .isEqualByComparingTo(cashFlow.amountAfter("Money Out"));
        assertThat(sankey.data("difference")).isEqualByComparingTo("1360.00")
                .isEqualByComparingTo(cashFlow.amountAfter("Difference"));
        assertThat(sankey.amountAfter("Money In")).isEqualByComparingTo("2725.00");
        assertThat(sankey.hasNode(TRADING)).isTrue();
    }

    @Test
    void everyFlowBelongsToTheSelectedAccountItMoved() {
        Report sankey = GnuCash.report(SANKEY_WITH_TRADING, SAMPLE);

        assertThat(sankey.hasHoverText(SALARY + " → " + CASH + ": €2,000.00")).isTrue();
        assertThat(sankey.hasHoverText("Equity" + DOT + "Opening Balances → " + CASH + ": €100.00")).isTrue();
        assertThat(sankey.hasHoverText(CASH + " → Expenses" + DOT + "Rent: €520.00")).isTrue();
        assertThat(sankey.hasHoverText(CASH + " → Expenses" + DOT + "Misc: €200.00")).isTrue();
        // Groceries is one level below the display depth of 2
        assertThat(sankey.hasHoverText(CASH + " → " + FOOD + ": €20.00")).isTrue();
        // The currency exchange has a selected account on both sides, so its trading splits are the counter
        // accounts, as in the standard report: each belongs to the selected split of the opposite sign.
        assertThat(sankey.hasHoverText(CASH + " → " + TRADING + ": €300.00")).isTrue();
        assertThat(sankey.hasHoverText(TRADING + " → " + CASH_USD + ": €300.00")).isTrue();
        assertThat(sankey.hasHoverText(CASH_USD + " → " + TRADING + ": €325.00")).isTrue();
        assertThat(sankey.hasHoverText(TRADING + " → " + CASH + ": €325.00")).isTrue();
        assertThat(sankey.hasHoverText(CASH + ": +€2,425.00 / −€1,040.00")).isTrue();
        assertThat(sankey.hasHoverText(CASH_USD + ": +€300.00 / −€325.00")).isTrue();
    }

    @Test
    void totalsStayTheCashFlowReportsWhenTransactionsTouchSeveralSelectedAccounts() {
        // trading flows hidden: the currency exchange of 300.00 out and 325.00 back is left out
        Report sankey = GnuCash.report(SANKEY, RICH);
        Report cashFlow = GnuCash.report(CASH_FLOW_WITHOUT_TRADING, RICH);

        assertThat(sankey.data("money-in")).isEqualByComparingTo("3127.50")
                .isEqualByComparingTo(cashFlow.amountAfter("Money In"));
        assertThat(sankey.data("money-out")).isEqualByComparingTo("816.00")
                .isEqualByComparingTo(cashFlow.amountAfter("Money Out"));
        assertThat(sankey.data("difference")).isEqualByComparingTo("2311.50")
                .isEqualByComparingTo(cashFlow.amountAfter("Difference"));

        // trading flows shown
        Report sankeyWithTrading = GnuCash.report(SANKEY_WITH_TRADING, RICH);
        Report cashFlowWithTrading = GnuCash.report(CASH_FLOW_WITH_TRADING, RICH);

        assertThat(sankeyWithTrading.data("money-in")).isEqualByComparingTo("3752.50")
                .isEqualByComparingTo(cashFlowWithTrading.amountAfter("Money In"));
        assertThat(sankeyWithTrading.data("money-out")).isEqualByComparingTo("1441.00")
                .isEqualByComparingTo(cashFlowWithTrading.amountAfter("Money Out"));
        assertThat(sankeyWithTrading.data("difference")).isEqualByComparingTo("2311.50")
                .isEqualByComparingTo(cashFlowWithTrading.amountAfter("Difference"));
    }

    @Test
    void aCounterSplitIsSharedBetweenTheSelectedAccountsInProportion() {
        Report sankey = GnuCash.report(SANKEY, RICH);

        // the bonus of 1,000.00 arrived as 600.00 in Cash and 400.00 in Checking
        assertThat(sankey.hasHoverText(SALARY + " → " + CASH + ": €2,600.00")).isTrue();
        assertThat(sankey.hasHoverText(SALARY + " → " + CHECKING + ": €400.00")).isTrue();
        // the dinner of 60.00 was paid 20.00 from Cash and 40.00 from Checking; Cash also bought groceries for 20.00
        assertThat(sankey.hasHoverText(CASH + " → " + FOOD + ": €40.00")).isTrue();
        assertThat(sankey.hasHoverText(CHECKING + " → " + FOOD + ": €40.00")).isTrue();
        // a refund is money in from an expense account
        assertThat(sankey.hasHoverText(FOOD + " → " + CASH + ": €15.00")).isTrue();
    }

    @Test
    void transfersBetweenSelectedAccountsStayHidden() {
        Report sankey = GnuCash.report(SANKEY, RICH);

        // 50.00 went from Cash to Checking; neither side shows it
        assertThat(sankey.svg()).doesNotContain("<title>" + CASH + " → " + CHECKING)
                .doesNotContain("<title>" + CHECKING + " → " + CASH);
        // Checking: interest 12.50 + bonus 400.00 in; dinner 40.00 + leisure 16.00 out
        assertThat(sankey.hasHoverText(CHECKING + ": +€412.50 / −€56.00")).isTrue();
    }

    @Test
    void counterAccountsBelowTheMinimumShareMergeIntoOther() {
        // trading flows hidden; 5 % of the 3,127.50 that came in is 156.38
        Report sankey = GnuCash.report(SANKEY + " merged", RICH);

        // in: Opening Balances 100.00 + Food (the refund) 15.00 + Interest 12.50
        assertThat(sankey.hasHoverText("Other: €127.50")).isTrue();
        // out: Food 80.00 + Leisure 16.00
        assertThat(sankey.hasHoverText("Other: €96.00")).isTrue();
        assertThat(sankey.hasNode(FOOD)).isFalse();
        assertThat(sankey.hasNode(SALARY)).isTrue();
        assertThat(sankey.data("money-in")).isEqualByComparingTo("3127.50");
        assertThat(sankey.data("money-out")).isEqualByComparingTo("816.00");
    }
}
