// A form marked data-submit-once sends one request. Pressing Enter and then the button would
// otherwise post twice; for one-time codes the second post is refused, and when the first one
// rotated the session id it arrives without a session.
document.addEventListener('submit', function (event) {
    var form = event.target;
    if (!(form instanceof HTMLFormElement) || !form.hasAttribute('data-submit-once')) {
        return;
    }
    if (form.dataset.submitted === 'true') {
        event.preventDefault();
        return;
    }
    form.dataset.submitted = 'true';
    form.querySelectorAll('button[type="submit"]').forEach(function (button) {
        button.disabled = true;
    });
});
