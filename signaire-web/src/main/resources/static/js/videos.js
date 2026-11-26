// The videos of a sign play once, at the normal speed (docs/design/web-ui.md). The button under each, hidden without
// script, plays it again at half speed, once: when that play ends, the speed is the normal one again, for the
// browser's controls as for the next press.
document.querySelectorAll('.player').forEach((player) => {
    const video = player.querySelector('video');
    const slow = player.querySelector('button.slow');
    if (!video || !slow) {
        return;
    }
    slow.hidden = false;
    video.addEventListener('ended', () => {
        video.playbackRate = video.defaultPlaybackRate;
    });
    slow.addEventListener('click', () => {
        video.currentTime = 0;
        video.playbackRate = 0.5;
        video.play().catch(() => {
            // the browser refused to play: its controls still can
            video.playbackRate = video.defaultPlaybackRate;
        });
    });
});
