package org.duckdns.gory26.gnucash.togo.web;

import org.duckdns.gory26.gnucash.book.AccountSummary;
import org.duckdns.gory26.gnucash.book.GnuCashBook;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/** Renders the entry form fragment, shared by the entry page and the edit page. */
@Component
class EntryView {

    static final String FORM = "fragments/entry-form :: form";

    private final Formats fmt;
    private final Clock clock;

    EntryView(Formats fmt, Clock clock) {
        this.fmt = fmt;
        this.clock = clock;
    }

    /** Puts the form on the model with its pickers' labels and currencies resolved. */
    void prepare(EntryForm form, GnuCashBook book, Model model, Map<String, String> errors) {
        Optional<AccountSummary> from = form.getFrom().isBlank() ? Optional.empty() : book.account(form.getFrom());
        Optional<AccountSummary> to = form.getTo().isBlank() ? Optional.empty() : book.account(form.getTo());
        if(form.getFromLabel().isBlank()) {
            from.ifPresent(a -> form.setFromLabel(a.path()));
        }
        if(form.getToLabel().isBlank()) {
            to.ifPresent(a -> form.setToLabel(a.path()));
        }
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("fromCurrency", from.map(a -> fmt.symbol(a.commodity())).orElse(""));
        // Decimals the amount is typed with (entry-form.js); cents until the from account is picked.
        model.addAttribute("fromDecimals", from.map(a -> Formats.decimalsOf(a.commodity().fraction())).orElse(2));
        model.addAttribute("toCurrency", to.map(a -> fmt.symbol(a.commodity())).orElse(""));
        model.addAttribute("today", LocalDate.now(clock));
    }

    /** The form fragment alone, as htmx swaps it in. */
    String fragment(EntryForm form, GnuCashBook book, Model model, Map<String, String> errors) {
        prepare(form, book, model, errors);
        return FORM;
    }
}
