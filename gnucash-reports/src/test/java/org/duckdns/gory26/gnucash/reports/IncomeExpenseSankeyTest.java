package org.duckdns.gory26.gnucash.reports;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Income &amp; Expense Sankey against GnuCash's own Income Statement, both run by GnuCash 5.16 on the same
 * book for November 2026 in EUR. The amounts written out here were worked out by hand from the books' rows.
 */
class IncomeExpenseSankeyTest {

    private static final String SAMPLE = Books.sample();
    private static final String RICH = Books.rich();
    private static final String LOSS = Books.loss();

    private static final String SANKEY = "Income Expense Sankey November";
    private static final String STATEMENT = "Income Statement November";
    private static final String DOT = GnuCash.SEPARATOR;

    @Test
    void totalsAreTheIncomeStatements() {
        Report sankey = GnuCash.report(SANKEY, SAMPLE);
        Report statement = GnuCash.report(STATEMENT, SAMPLE);

        assertThat(sankey.data("revenue")).isEqualByComparingTo("2000.00")
                .isEqualByComparingTo(statement.amountAfter("Total Revenue"));
        assertThat(sankey.data("expenses")).isEqualByComparingTo("740.00")
                .isEqualByComparingTo(statement.amountAfter("Total Expenses"));
        assertThat(sankey.data("net-income")).isEqualByComparingTo("1260.00")
                .isEqualByComparingTo(statement.amountAfter("Net income for Period"));
        // the same totals as text below the diagram
        assertThat(sankey.amountAfter("Total Revenue")).isEqualByComparingTo("2000.00");
        assertThat(sankey.amountAfter("Total Expenses")).isEqualByComparingTo("740.00");
        assertThat(sankey.amountAfter("Net income")).isEqualByComparingTo("1260.00");
    }

    @Test
    void incomeFlowsThroughTheHubToExpensesAndNetIncome() {
        Report sankey = GnuCash.report(SANKEY, SAMPLE);

        assertThat(sankey.hasHoverText("Salary → Income: €2,000.00")).isTrue();
        assertThat(sankey.hasHoverText("Income → Revenue: €2,000.00")).isTrue();
        assertThat(sankey.hasHoverText("Revenue: €2,000.00")).isTrue();
        assertThat(sankey.hasHoverText("Revenue → Expenses: €740.00")).isTrue();
        assertThat(sankey.hasHoverText("Expenses → Rent: €520.00")).isTrue();
        assertThat(sankey.hasHoverText("Expenses → Misc: €200.00")).isTrue();
        assertThat(sankey.hasHoverText("Expenses → Food: €20.00")).isTrue();
        assertThat(sankey.hasHoverText("Food → Groceries: €20.00")).isTrue();
        assertThat(sankey.hasHoverText("Revenue → Net income: €1,260.00")).isTrue();
    }

    @Test
    void selectedTradingAccountsCountAsInTheIncomeStatement() {
        Report sankey = GnuCash.report(SANKEY + " with trading", SAMPLE);
        Report statement = GnuCash.report(STATEMENT + " with trading", SAMPLE);

        assertThat(sankey.data("trading")).isEqualByComparingTo("-25.00")
                .isEqualByComparingTo(statement.amountAfter("Total Trading"));
        assertThat(sankey.data("net-income")).isEqualByComparingTo("1235.00")
                .isEqualByComparingTo(statement.amountAfter("Net income for Period"));
        // a trading balance that lowers net income leaves the hub like an expense
        assertThat(sankey.hasHoverText(
                "Revenue → Trading" + DOT + "CURRENCY" + DOT + "EUR: €25.00")).isTrue();
        assertThat(sankey.hasHoverText("Revenue → Net income: €1,235.00")).isTrue();
    }

    @Test
    void totalsStayTheIncomeStatementsWithRefundsAndSeveralIncomeAccounts() {
        Report sankey = GnuCash.report(SANKEY, RICH);
        Report statement = GnuCash.report(STATEMENT, RICH);

        assertThat(sankey.data("revenue")).isEqualByComparingTo("3012.50")
                .isEqualByComparingTo(statement.amountAfter("Total Revenue"));
        assertThat(sankey.data("expenses")).isEqualByComparingTo("801.00")
                .isEqualByComparingTo(statement.amountAfter("Total Expenses"));
        assertThat(sankey.data("net-income")).isEqualByComparingTo("2211.50")
                .isEqualByComparingTo(statement.amountAfter("Net income for Period"));
    }

    @Test
    void anExpenseAccountWithNetRefundsFeedsTheHubUnderItsFullName() {
        Report sankey = GnuCash.report(SANKEY, RICH);

        String refunds = "Expenses" + DOT + "Food" + DOT + "Refunds";
        // 3,012.50 of revenue and 15.00 of refunds come in, so the hub is no longer revenue alone
        assertThat(sankey.hasHoverText(refunds + " → Total: €15.00")).isTrue();
        assertThat(sankey.hasHoverText("Total: €3,027.50")).isTrue();
        // and Food shows what was spent, not spent less refunded
        assertThat(sankey.hasHoverText("Expenses → Food: €80.00")).isTrue();
        assertThat(sankey.hasHoverText("Total → Expenses: €816.00")).isTrue();
        assertThat(sankey.hasHoverText("Total → Net income: €2,211.50")).isTrue();
    }

    @Test
    void aLossIsDrawnFeedingTheHub() {
        Report sankey = GnuCash.report(SANKEY, LOSS);
        Report statement = GnuCash.report(STATEMENT, LOSS);

        BigDecimal loss = statement.amountAfter("Net loss for Period");
        assertThat(loss).isEqualByComparingTo("3740.00");
        assertThat(sankey.data("net-income")).isEqualByComparingTo(loss.negate());
        assertThat(sankey.hasHoverText("Net loss → Total: €3,740.00")).isTrue();
        assertThat(sankey.hasHoverText("Total → Expenses: €5,740.00")).isTrue();
        assertThat(sankey.hasNode("Net income")).isFalse();
        assertThat(sankey.amountAfter("Net loss")).isEqualByComparingTo("3740.00");
    }

    @Test
    void accountsBelowTheMinimumShareMergeIntoOtherUnderTheirParent() {
        // 5 % of the hub's 3,027.50 is 151.38: Food (80.00) and Leisure (16.00) fall below it
        Report sankey = GnuCash.report(SANKEY + " merged", RICH);

        assertThat(sankey.hasHoverText("Expenses → Other: €96.00")).isTrue();
        assertThat(sankey.hasNode("Food")).isFalse();
        assertThat(sankey.hasNode("Groceries")).isFalse();
        assertThat(sankey.hasNode("Leisure")).isFalse();
        assertThat(sankey.hasNode("Rent")).isTrue();
        // a single small account is not renamed to "Other"
        assertThat(sankey.hasHoverText("Interest → Income: €12.50")).isTrue();
        assertThat(sankey.data("expenses")).isEqualByComparingTo("801.00");
        assertThat(sankey.data("net-income")).isEqualByComparingTo("2211.50");
    }

    @Test
    void accountsBelowTheDepthLimitCountIntoTheirAncestor() {
        Report sankey = GnuCash.report(SANKEY + " two levels", RICH);

        // Groceries 20.00 + Restaurants 60.00 - Refunds 15.00, netted inside Food
        assertThat(sankey.hasHoverText("Expenses → Food: €65.00")).isTrue();
        assertThat(sankey.hasNode("Groceries")).isFalse();
        assertThat(sankey.hasNode("Expenses" + DOT + "Food" + DOT + "Refunds")).isFalse();
        assertThat(sankey.hasHoverText("Revenue: €3,012.50")).isTrue();
        assertThat(sankey.data("expenses")).isEqualByComparingTo("801.00");
    }
}
