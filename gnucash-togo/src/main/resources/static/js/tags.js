// Tag chips on the entry form: the app's only hand-written script (docs/plans/gnucash-togo.md, M4).
// Enter, a comma or a picked suggestion turns the typed text into a chip; × removes one; Backspace in the
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
