// A step of the guided tour (dialog#tour, rendered by the server) next to the element it presents
// (data-tour = the data-target of the dialog), the rest of the page dimmed. Without this script, the dialog is a card
// at the top of the page.
(() => {
    const dialog = document.getElementById("tour");
    if (!dialog) {
        return;
    }
    const target = document.querySelector(`[data-tour="${dialog.dataset.target}"]`);
    const margin = 12;

    function place() {
        if (!target) {
            return;
        }
        const element = target.getBoundingClientRect();
        const box = dialog.getBoundingClientRect();
        let top = element.bottom + margin;
        if (top + box.height > window.innerHeight && element.top - margin - box.height > 0) {
            top = element.top - margin - box.height;
        }
        top = Math.max(margin, Math.min(top, window.innerHeight - box.height - margin));
        const left = Math.max(margin, Math.min(element.left, window.innerWidth - box.width - margin));
        dialog.style.top = `${top}px`;
        dialog.style.left = `${left}px`;
    }

    function close() {
        dialog.close();
        target?.classList.remove("tour-target");
        dialog.classList.remove("floating", "centered");
    }

    dialog.classList.add("floating");
    if (target) {
        target.classList.add("tour-target");
        target.scrollIntoView({block: "center"});
    } else {
        dialog.classList.add("centered");
    }
    place();
    window.addEventListener("resize", place);
    window.addEventListener("scroll", place, {passive: true});
    document.addEventListener("keydown", event => {
        if (event.key === "Escape" && dialog.open) {
            close();
        }
    });
    dialog.focus();
})();
