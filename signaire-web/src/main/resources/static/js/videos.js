// The videos of a sign play once (docs/design/web-ui.md). The button under each, hidden without script, plays it
// again at half speed, and at the normal speed when pressed again.
document.querySelectorAll('.player').forEach((player) => {
    const video = player.querySelector('video');
    const slow = player.querySelector('button.slow');
    if (!video || !slow) {
        return;
    }
    slow.hidden = false;
    slow.addEventListener('click', () => {
        const pressed = slow.getAttribute('aria-pressed') !== 'true';
        slow.setAttribute('aria-pressed', String(pressed));
        // the default too: a video that loads again (preload="none") starts at it
        video.defaultPlaybackRate = pressed ? 0.5 : 1;
        video.playbackRate = video.defaultPlaybackRate;
        video.currentTime = 0;
        video.play().catch(() => {
            // the browser refused to play: its controls still can
        });
    });
});
