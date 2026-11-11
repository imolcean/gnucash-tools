package org.duckdns.gory26.gnucash.togo.web;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * What the entry form holds, exactly as typed, so a refused or invalid save can show it again unchanged.
 * {@code fromLabel}/{@code toLabel} are the picker boxes' text; {@code from}/{@code to} the picked GUIDs.
 * {@code txGuid} is set when the form edits an existing transaction.
 */
public class EntryForm {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;
    private List<String> tags = new ArrayList<>();
    /** Text left in the tag box, not yet a chip; saved as a tag too. */
    private String tag = "";
    private String text = "";
    private String amount = "";
    private String from = "";
    private String fromLabel = "";
    private String to = "";
    private String toLabel = "";
    private String notes = "";
    private String txGuid = "";
    private String returnTo = "";

    public boolean isEditing() {
        return !txGuid.isBlank();
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = clean(tag);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = clean(text);
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = clean(amount);
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = clean(from);
    }

    public String getFromLabel() {
        return fromLabel;
    }

    public void setFromLabel(String fromLabel) {
        this.fromLabel = clean(fromLabel);
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = clean(to);
    }

    public String getToLabel() {
        return toLabel;
    }

    public void setToLabel(String toLabel) {
        this.toLabel = clean(toLabel);
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes == null ? "" : notes;
    }

    public String getTxGuid() {
        return txGuid;
    }

    public void setTxGuid(String txGuid) {
        this.txGuid = clean(txGuid);
    }

    public String getReturnTo() {
        return returnTo;
    }

    public void setReturnTo(String returnTo) {
        this.returnTo = clean(returnTo);
    }

    static EntryForm empty(LocalDate date) {
        EntryForm form = new EntryForm();
        form.setDate(date);
        return form;
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    /** The chips plus any text left in the tag box, trimmed, without blanks or repeats. */
    List<String> allTags() {
        LinkedHashSet<String> all = new LinkedHashSet<>();
        for(String t : tags) {
            if(!t.isBlank()) {
                all.add(t.strip());
            }
        }
        if(!tag.isBlank()) {
            all.add(tag.strip());
        }
        return List.copyOf(all);
    }
}
