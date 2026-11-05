package org.duckdns.gory26.gnucash.togo.web;

import org.duckdns.gory26.gnucash.book.AccountChoice;
import org.duckdns.gory26.gnucash.book.AccountSummary;
import org.duckdns.gory26.gnucash.book.GnuCashBook;
import org.duckdns.gory26.gnucash.book.QuickFill;
import org.duckdns.gory26.gnucash.book.TaggedDescription;
import org.duckdns.gory26.gnucash.book.TransferDraft;
import org.duckdns.gory26.gnucash.book.WriteRefusedException;
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

    private final Formats fmt;
    private final Clock clock;
    private final EntryView view;

    EntryController(Formats fmt, Clock clock, EntryView view) {
        this.fmt = fmt;
        this.clock = clock;
        this.view = view;
    }

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
        if (!check.errors().isEmpty()) {
            return view.fragment(form, book, model, check.errors());
        }
        TransferDraft draft = check.draft().orElseThrow();
        try {
            book.insert(draft);
        } catch (WriteRefusedException e) {
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

    // ---------------------------------------------------------------- pickers

    @GetMapping("/suggest/tags")
    String tags(@AuthenticationPrincipal Session session, @RequestParam(defaultValue = "") String tag,
                @RequestParam(defaultValue = "") List<String> tags, Model model) {
        model.addAttribute("tags", session.book().suggestTags(tag).stream().filter(t -> !tags.contains(t)).toList());
        return "fragments/suggestions :: tags";
    }

    @GetMapping("/suggest/descriptions")
    String descriptions(@AuthenticationPrincipal Session session, @RequestParam(defaultValue = "") String text,
                        Model model) {
        model.addAttribute("descriptions", text.isBlank() ? List.of() : session.book().suggestDescriptions(text));
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
        if (!field.equals("from") && !field.equals("to")) {
            throw new IllegalArgumentException("no such picker: " + field);
        }
        String typed = params.getOrDefault(field + "Label", "");
        String picked = params.getOrDefault(field, "");
        List<AccountChoice> choices = session.book().suggestAccounts(typed);
        boolean stillPicked = !picked.isBlank()
                && session.book().account(picked).map(a -> a.path().equals(typed.strip())).orElse(false);
        model.addAttribute("field", field);
        model.addAttribute("choices", choices.stream()
                .map(c -> new Choice(c.guid(), c.path(), fmt.symbol(c.commodity())))
                .toList());
        model.addAttribute("unpick", !stillPicked && !picked.isBlank());
        return "fragments/suggestions :: accounts";
    }

    record Choice(String guid, String path, String currency) {
    }

    /** Closes a suggestion list when its box loses focus. */
    @GetMapping("/suggest/none")
    String none() {
        return "fragments/suggestions :: none";
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
        fill.ifPresent(f -> {
            form.setText(f.text());
            form.setTags(new ArrayList<>(f.tags()));
            form.setTag("");
            form.setFrom(f.fromAccountGuid());
            form.setFromLabel("");
            form.setTo(f.toAccountGuid());
            form.setToLabel("");
            book.account(f.fromAccountGuid())
                    .ifPresent(from -> form.setAmount(fmt.input(f.amount(), from.commodity())));
        });
        return view.fragment(form, book, model, Map.of());
    }

    /** A picked account: {@code pick} is {@code from:GUID} or {@code to:GUID}. */
    @PostMapping("/entry/pick")
    String pick(@AuthenticationPrincipal Session session, @ModelAttribute("form") EntryForm form,
                @RequestParam String pick, Model model) {
        String[] parts = pick.split(":", 2);
        if (parts.length == 2 && parts[0].equals("from")) {
            form.setFrom(parts[1]);
            form.setFromLabel("");
        } else if (parts.length == 2 && parts[0].equals("to")) {
            form.setTo(parts[1]);
            form.setToLabel("");
        }
        return view.fragment(form, session.book(), model, Map.of());
    }
}
