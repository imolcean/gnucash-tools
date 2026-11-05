package org.duckdns.gory26.gnucash.togo;

import org.duckdns.gory26.gnucash.book.SampleBook;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;

/** "Today" for the app under test is the sample book's {@link SampleBook#TODAY}. */
@TestConfiguration(proxyBeanMethods = false)
class FixedClock {

    @Bean
    @Primary
    Clock sampleBookToday() {
        return SampleBook.TODAY;
    }
}
