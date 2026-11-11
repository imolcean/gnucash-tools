package org.duckdns.gory26.gnucash.book;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The latest price per commodity pair, for converting rollups into a parent's commodity (ADR-0008). */
final class Prices {

    private record Quote(LocalDateTime date,
                         GncNumeric value) {
    }

    /** Keyed by {@code commodity_guid + ">" + currency_guid}: one unit of the commodity costs value of the currency. */
    private final Map<String, Quote> latest;

    private Prices(Map<String, Quote> latest) {
        this.latest = latest;
    }

    static Prices load(JdbcClient jdbc) {
        record PriceRow(String commodity,
                        String currency,
                        LocalDateTime date,
                        GncNumeric value) {
        }
        List<PriceRow> rows = jdbc.sql("SELECT commodity_guid, currency_guid, date, value_num, value_denom FROM prices")
                .query((rs, _) -> new PriceRow(rs.getString(1), rs.getString(2),
                        rs.getObject(3, LocalDateTime.class), new GncNumeric(rs.getLong(4), rs.getLong(5))))
                .list();
        Map<String, Quote> latest = new HashMap<>();
        for(PriceRow p : rows) {
            if(p.value().isZero()) {
                continue;
            }
            latest.merge(p.commodity() + ">" + p.currency(), new Quote(p.date(), p.value()),
                    (a, b) -> b.date().isAfter(a.date()) ? b : a);
        }
        return new Prices(latest);
    }

    /**
     * The rate that turns an amount of {@code from} into {@code to}: the newer of the direct quote and the
     * inverted reverse quote, preferring the direct one on the same date. Desktop's indirect conversion
     * through a third commodity is not attempted.
     */
    Optional<GncNumeric> rate(String from, String to) {
        Quote direct = latest.get(from + ">" + to);
        Quote inverse = latest.get(to + ">" + from);
        if(direct != null && (inverse == null || !inverse.date().isAfter(direct.date()))) {
            return Optional.of(direct.value());
        }
        return Optional.ofNullable(inverse).map(q -> q.value().invert());
    }
}
