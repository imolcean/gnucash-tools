package org.duckdns.gory26.gnucash.togo.web;

import org.duckdns.gory26.gnucash.book.SampleBook;
import org.duckdns.gory26.gnucash.togo.AppTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Pages of two rows split 03.11.2026 (three Cash rows) across pages two and three. */
@TestPropertySource(properties = "togo.register.page-size=2")
class RegisterPagingTest extends AppTest {

    @Test
    void loadMoreContinuesADayWithoutRepeatingItsHeader() throws Exception {
        MockHttpSession session = login();
        String first = get(session, "/accounts/" + SampleBook.CASH);
        assertThat(first).contains("Tue 10.11.2026").contains("Wed 04.11.2026").doesNotContain("03.11.2026");

        String second = get(session, RegisterTest.loadMore(first));
        assertThat(second).contains("hx-swap-oob=\"true\"")
                .contains("Tue 03.11.2026").contains("Buy USD @ 1.2").contains("Storage room")
                .doesNotContain("Wed 04.11.2026");

        String third = get(session, RegisterTest.loadMore(second));
        assertThat(third).contains("Salary").doesNotContain("03.11.2026")
                .contains("Mon 02.11.2026").contains("Rewe again");

        String fourth = get(session, RegisterTest.loadMore(third));
        assertThat(fourth).contains("Rewe").contains("Sun 01.11.2026").contains("Opening Balance");
        assertThat(RegisterTest.loadMore(fourth)).as("the last page has no Load more").isNull();
    }

    private String get(MockHttpSession session, String url) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url).session(session))
                .andReturn().getResponse().getContentAsString();
    }
}
