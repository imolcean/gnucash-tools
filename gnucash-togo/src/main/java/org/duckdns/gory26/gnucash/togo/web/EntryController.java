package org.duckdns.gory26.gnucash.togo.web;

import org.duckdns.gory26.gnucash.book.*;
import org.duckdns.gory26.gnucash.togo.session.Session;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The entry form at {@code /} and the htmx endpoints behind its pickers. Every save answers 200 with the form
 * re-rendered: htmx 2 does not swap 4xx/5xx responses, and an invalid or refused save must keep what was typed.
 */
@Controller
class EntryController {

    record Choice(String guid,
                  String path,
                  String currency) {
    }
    /** Items in a suggestion list: few enough to fit on a phone above its keyboard, with no scroll bar. */
    static final int SHOWN = 4;
    private final Formats fmt;
    private final Clock clock;
    private final EntryView view;

    EntryController(Formats fmt, Clock clock, EntryView view) {
        this.fmt = fmt;
        this.clock = clock;
        this.view = view;
    }

    private static String firstMatch(GnuCashBook book, String typed) {
        return typed.isBlank() ? "" : book.suggestAccounts(typed).stream().findFirst().map(AccountChoice::guid).orElse("");
    }

    // ---------------------------------------------------------------- pickers

    @GetMapping("/")
    String entry(@AuthenticationPrincipal Session session, Model model) {
        view.prepare(EntryForm.empty(LocalDate.now(clock)), session.book(), model, Map.of());
        return "entry";
    }

    /** Saves a new transfer; afterwards the form is empty again except for the date, and says what was saved. */
    @PostMapping("/")
    String save(@AuthenticationPrincipal Session session, @ModelAttribute("form") EntryForm form,
                BindingResult binding, Model model) {
        GnuCashBook book = session.book();
        EntryCheck check = EntryCheck.of(form, binding, book);
        if(!check.errors().isEmpty()) {
            return view.fragment(form, book, model, check.errors());
        }
        TransferDraft draft = check.draft().orElseThrow();
        try {
            book.insert(draft);
        }
        catch(WriteRefusedException e) {
            model.addAttribute("refusal", e.getMessage());
            return view.fragment(form, book, model, check.errors());
        }
        model.addAttribute("saved", savedLine(draft, check.from().orElseThrow(), check.to().orElseThrow()));
        return view.fragment(EntryForm.empty(draft.date()), book, model, Map.of());
    }

    /** "[Tag 1] Rewe · 12.34 € · Cash → Groceries" */
    private String savedLine(TransferDraft draft, AccountSummary from, AccountSummary to) {
        String description = new TaggedDescription(draft.tags(), draft.text()).compose();
        return (description.isEmpty() ? "" : description + " · ") + fmt.amount(draft.amount(), from.commodity())
                + " · " + from.name() + " → " + to.name();
    }

    @GetMapping("/suggest/tags")
    String tags(@AuthenticationPrincipal Session session, @RequestParam(defaultValue = "") String tag,
                @RequestParam(defaultValue = "") List<String> tags, Model model) {
        model.addAttribute("tags", session.book().suggestTags(tag).stream()
                .filter(t -> !tags.contains(t)).limit(SHOWN).toList());
        return "fragments/suggestions :: tags";
    }

    @GetMapping("/suggest/descriptions")
    String descriptions(@AuthenticationPrincipal Session session, @RequestParam(defaultValue = "") String text,
                        Model model) {
        model.addAttribute("descriptions",
                text.isBlank() ? List.of() : session.book().suggestDescriptions(text).stream().limit(SHOWN).toList());
        return "fragments/suggestions :: descriptions";
    }

    /**
     * Accounts matching what is typed in one picker; an empty box lists the most used ones. Typing anything but
     * the picked account's path un-picks it, so a stale GUID can't be saved.
     *
     * @param field {@code from} or {@code to}
     */
    @GetMapping("/suggest/accounts")
    String accounts(@AuthenticationPrincipal Session session, @RequestParam String field,
                    @RequestParam Map<String, String> params, Model model) {
        if(!field.equals("from") && !field.equals("to")) {
            throw new IllegalArgumentException("no such picker: " + field);
        }
        String typed = params.getOrDefault(field + "Label", "");
        String picked = params.getOrDefault(field, "");
        List<AccountChoice> choices = session.book().suggestAccounts(typed);
        boolean stillPicked = !picked.isBlank()
                && session.book().account(picked).map(a -> a.path().equals(typed.strip())).orElse(false);
        model.addAttribute("field", field);
        model.addAttribute("choices", choices.stream().limit(SHOWN)
                .map(c -> new Choice(c.guid(), c.path(), fmt.symbol(c.commodity())))
                .toList());
        model.addAttribute("unpick", !stillPicked && !picked.isBlank());
        return "fragments/suggestions :: accounts";
    }

    /** Closes a suggestion list when its box loses focus. */
    @GetMapping("/suggest/none")
    String none() {
        return "fragments/suggestions :: none";
    }

    /**
     * The result line under an amount like {@code 12.30 + 4.50}: {@code = 16.80 €}, or why it can't be calculated;
     * empty for a plain amount. On leaving the field ({@code done}) a valid sum is replaced by its result, as
     * Desktop does on Tab.
     *
     * @param from the from-account's GUID: its commodity sets the decimals and the rounding of a division
     */
    @GetMapping("/entry/amount")
    String amount(@AuthenticationPrincipal Session session, @RequestParam(defaultValue = "") String amount,
                  @RequestParam(defaultValue = "") String from, @RequestParam(defaultValue = "false") boolean done,
                  Model model) {
        Optional<AccountSummary> account = from.isBlank() ? Optional.empty() : session.book().account(from);
        model.addAttribute("fromDecimals", account.map(a -> Formats.decimalsOf(a.commodity().fraction())).orElse(2));
        if(AmountExpression.isPlain(amount)) {
            return "fragments/suggestions :: amount";
        }
        long fraction = account.map(a -> a.commodity().fraction()).orElse(100L);
        try {
            GncNumeric result = GncNumeric.of(AmountExpression.evaluate(amount, fraction), fraction);
            if(done && result.signum() > 0) {
                // the field shows the result now, so the line under it goes
                model.addAttribute("replace", account.map(a -> fmt.input(result, a.commodity()))
                        .orElseGet(() -> result.toBigDecimal().toPlainString()));
            }
            else {
                model.addAttribute("result", account.map(a -> fmt.amount(result, a.commodity()))
                        .orElseGet(() -> result.toBigDecimal().toPlainString()));
            }
        }
        catch(InvalidAmountException e) {
            model.addAttribute("error", EntryCheck.amountError(e.reason()));
        }
        return "fragments/suggestions :: amount";
    }

    @GetMapping("/entry/today")
    String today(Model model) {
        model.addAttribute("today", LocalDate.now(clock));
        return "fragments/suggestions :: today";
    }

    /** A picked description fills tags, accounts and amount from its latest two-split use. */
    @PostMapping("/entry/quickfill")
    String quickFill(@AuthenticationPrincipal Session session, @ModelAttribute("form") EntryForm form,
                     @RequestParam String pick, Model model) {
        GnuCashBook book = session.book();
        form.setText(pick);
        Optional<QuickFill> fill = book.quickFill(pick);
        // The description comes after from, to and amount: fill only what the user hasn't entered yet.
        fill.ifPresent(f -> {
            form.setText(f.text());
            if(form.getTags().isEmpty()) {
                form.setTags(new ArrayList<>(f.tags()));
                form.setTag("");
            }
            if(form.getFrom().isEmpty()) {
                form.setFrom(f.fromAccountGuid());
                form.setFromLabel("");
            }
            if(form.getTo().isEmpty()) {
                form.setTo(f.toAccountGuid());
                form.setToLabel("");
            }
            if(form.getAmount().isEmpty()) {
                book.account(form.getFrom())
                        .ifPresent(from -> form.setAmount(fmt.input(f.amount(), from.commodity())));
            }
        });
        return view.fragment(form, book, model, Map.of());
    }

    /** A picked account: {@code pick} is {@code from:GUID} or {@code to:GUID}. */
    @PostMapping(path = "/entry/pick", params = "pick")
    String pick(@AuthenticationPrincipal Session session, @ModelAttribute("form") EntryForm form,
                @RequestParam String pick, Model model) {
        String[] parts = pick.split(":", 2);
        if(parts.length == 2 && parts[0].equals("from")) {
            form.setFrom(parts[1]);
            form.setFromLabel("");
        }
        else if(parts.length == 2 && parts[0].equals("to")) {
            form.setTo(parts[1]);
            form.setToLabel("");
        }
        return view.fragment(form, session.book(), model, Map.of());
    }

    /**
     * Enter in a picker: the first account its list offers for the typed text, as if tapped; with none, the box
     * empties. The server looks it up, as the list on screen may still be for an earlier text.
     *
     * @param first {@code from} or {@code to}
     */
    @PostMapping(path = "/entry/pick", params = "first")
    String pickFirst(@AuthenticationPrincipal Session session, @ModelAttribute("form") EntryForm form,
                     @RequestParam String first, Model model) {
        GnuCashBook book = session.book();
        if(first.equals("from")) {
            form.setFrom(firstMatch(book, form.getFromLabel()));
            form.setFromLabel("");
        }
        else if(first.equals("to")) {
            form.setTo(firstMatch(book, form.getToLabel()));
            form.setToLabel("");
        }
        return view.fragment(form, book, model, Map.of());
    }
}
