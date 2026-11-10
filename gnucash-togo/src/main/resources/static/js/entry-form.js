// The entry form's keyboard: the app's only hand-written script (docs/plans/gnucash-togo.md, M4).
//
// Phones have no Tab key, so Enter on a field marked data-enter="next" (date, from, to, amount) moves to the next
// visible field instead of saving; Enter on the description saves as usual. Picking an account moves on the same way.
//
// An account box keeps only an account picked from its list: Enter takes the first suggestion, and typed text that
// was never picked is emptied on leaving. The next box opens its list as soon as it gets the focus.
//
// The amount is typed as on a card terminal: digits only, the point stays data-decimals places from the right,
// so 1, 2, 3 shows 0.01, 0.12, 1.23; Backspace takes the last digit off again. It also takes GnuCash's calculator
// (12.30 + 4.50 × 2): the operator strip under the field, or + - * / on a keyboard. The server calculates.
//
// Tag chips: Enter, a comma or a picked suggestion turns the typed text into a chip; × removes one; Backspace in the
// empty box removes the last. Brackets are refused: tags are stored as "[Tag] text" (ADR-0009).
// Each chip carries a hidden "tags" input. Listeners sit on document, so they survive htmx swapping the form.
(() => {
    const input = () => document.getElementById('tag-input');
    const chips = () => document.getElementById('chips');

    function chip(tag) {
        const span = document.createElement('span');
        span.className = 'chip';
        const label = document.createElement('span');
        label.textContent = tag;
        const hidden = document.createElement('input');
        hidden.type = 'hidden';
        hidden.name = 'tags';
        hidden.value = tag;
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.dataset.removeTag = '';
        remove.setAttribute('aria-label', 'Remove ' + tag);
        remove.textContent = '×';
        span.append(label, hidden, remove);
        return span;
    }

    function add(text) {
        const tag = text.trim();
        if (tag && ![...chips().querySelectorAll('input[name=tags]')].some(i => i.value === tag)) {
            chips().append(chip(tag));
        }
        input().value = '';
        document.getElementById('tag-suggestions').replaceChildren();
    }

    function shifted(text, decimals) {
        const digits = text.replace(/\D/g, '').replace(/^0+/, '');
        if (!digits) return '';
        const padded = digits.padStart(decimals + 1, '0');
        return decimals ? padded.slice(0, -decimals) + '.' + padded.slice(-decimals) : padded;
    }

    const OPERATOR = /([+−×÷])/;

    // "1230+450" → "12.30 + 4.50": money terms shift card-terminal style, a factor after × or ÷ is a whole number
    // as typed. Operators never lead; a second one in a row replaces the first.
    function expression(value, decimals) {
        const parts = value.replace(/\*/g, '×').replace(/\//g, '÷').replace(/-/g, '−').split(OPERATOR);
        let out = '';
        let op = null;
        parts.forEach((part, i) => {
            if (i % 2) {
                if (out) op = part;
                return;
            }
            const digits = part.replace(/\D/g, '');
            const term = op === '×' || op === '÷' ? digits.replace(/^0+(?=\d)/, '') : shifted(digits, decimals);
            if (!term) return;
            out += (op ? ' ' + op + ' ' : '') + term;
            op = null;
        });
        return out + (op ? ' ' + op + ' ' : '');
    }

    const shown = new WeakMap(); // the amount as last formatted, to tell Backspace over " + " apart

    function amountTyped(field, inputType) {
        const before = shown.get(field) ?? '';
        let value = field.value;
        if (inputType === 'deleteContentBackward' && / [+−×÷] $/.test(before) && value === before.slice(0, -1)) {
            value = before.slice(0, -3); // Backspace takes the whole operator, not just its space
        }
        field.value = expression(value, Number(field.dataset.decimals));
        shown.set(field, field.value);
    }

    document.addEventListener('input', e => {
        if (e.target.matches('[data-decimals]')) amountTyped(e.target, e.inputType);
    });

    // The strip's buttons act on pointerdown and cancel it, so the field keeps the focus and the phone keeps its
    // keyboard up (tested on Android, 2026-11-10). With the field not focused they do nothing.
    document.addEventListener('pointerdown', e => {
        const button = e.target.closest('[data-op]');
        if (!button) return;
        e.preventDefault();
        const field = document.getElementById('amount');
        if (document.activeElement !== field || !field.value.trim()) return;
        field.value = field.matches('[data-decimals]')
            ? field.value + button.dataset.op
            : field.value.replace(/\s*[+−×÷]\s*$/, '') + ' ' + button.dataset.op + ' ';
        field.dispatchEvent(new Event('input', {bubbles: true}));
    });

    // The visible field after this one. offsetParent is null for fields inside a closed <details>: those are skipped.
    function next(field) {
        const fields = [...field.form.querySelectorAll('input:not([type=hidden]), textarea')]
            .filter(f => !f.disabled && f.offsetParent !== null);
        return fields[fields.indexOf(field) + 1];
    }

    // Account pickers take only an account from the list. Typing un-picks at once, so a GUID never outlives the
    // text it was picked by; leaving the picker with nothing picked empties the box.
    const box = picker => picker.querySelector('input:not([type=hidden])');
    const guid = picker => picker.querySelector('input[type=hidden]');

    document.addEventListener('keydown', e => {
        if (e.key !== 'Enter' || e.isComposing || !e.target.matches('[data-enter=next]')) return;
        e.preventDefault();
        const picker = e.target.closest('[data-account-picker]');
        if (picker && e.target.value.trim() && !guid(picker).value) {
            // Enter on typed text takes the first suggestion. The list on screen may be for an earlier text (typing
            // waits 250 ms before asking), so the server looks it up; with no match it empties the box. The form is
            // the source, so the post can't queue behind the box's own suggestion request.
            picker.dataset.picking = ''; // leaving the box must not empty it before the answer
            htmx.ajax('POST', '/entry/pick', {source: e.target.form, values: {first: guid(picker).name}});
        }
        next(e.target)?.focus();
    });

    // A pick swaps the form, and htmx gives the focus back to the next box before it wires that box up, so the
    // box's own focus trigger misses: open its list here, as a tap on it would. Only after a form post: the list
    // arriving settles too, and must not ask for itself again.
    document.addEventListener('htmx:afterSettle', e => {
        if (e.detail.requestConfig?.verb !== 'post') return;
        const active = document.activeElement;
        const picker = active?.closest('[data-account-picker]');
        if (picker && active === box(picker)) htmx.trigger(active, 'focus');
    });

    document.addEventListener('input', e => {
        const picker = e.target.closest('[data-account-picker]');
        if (picker && e.target === box(picker)) guid(picker).value = '';
    });

    document.addEventListener('focusout', e => {
        const picker = e.target.closest('[data-account-picker]');
        if (picker && !picker.contains(e.relatedTarget) && !guid(picker).value && !('picking' in picker.dataset)) {
            box(picker).value = '';
        }
    });

    // A tapped suggestion keeps the box focused, as the operator strip does, then hands the focus to the next field
    // while the finger is still down: the keyboard stays up. htmx swaps the form for the picked state and gives the
    // focus back to that field by its id.
    document.addEventListener('pointerdown', e => {
        if (e.target.closest('[data-account-picker] .suggestions button')) e.preventDefault();
    });

    document.addEventListener('click', e => {
        const pick = e.target.closest('[data-account-picker] .suggestions button');
        if (!pick) return;
        const picker = pick.closest('[data-account-picker]');
        guid(picker).value = pick.value.split(':')[1]; // picked: leaving the box must not empty it
        next(box(picker))?.focus();
    });

    document.addEventListener('keydown', e => {
        if (e.target !== input()) return;
        if (e.key === 'Enter' || e.key === ',') {
            e.preventDefault();
            add(e.target.value);
        } else if (e.key === 'Backspace' && e.target.value === '') {
            const last = chips().lastElementChild;
            if (last) {
                e.preventDefault();
                last.remove();
            }
        }
    });

    // Phone keyboards often send no usable key for the comma, so look at the text as well.
    document.addEventListener('input', e => {
        if (e.target !== input()) return;
        let value = e.target.value.replace(/[\[\]]/g, '');
        if (value.includes(',')) {
            const parts = value.split(',');
            value = parts.pop();
            parts.forEach(add);
        }
        e.target.value = value;
    });

    document.addEventListener('click', e => {
        const remove = e.target.closest('[data-remove-tag]');
        if (remove) {
            remove.closest('.chip').remove();
            return;
        }
        const pick = e.target.closest('#tag-suggestions [data-tag]');
        if (pick) {
            add(pick.dataset.tag);
            input().focus();
        }
    });
})();
