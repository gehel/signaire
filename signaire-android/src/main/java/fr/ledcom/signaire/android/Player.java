package fr.ledcom.signaire.android;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.ledcom.signaire.model.Media;

/**
 * Videos and images of a sign: from their bundle when they were downloaded, from SignSuisse otherwise. A video plays
 * once, at the normal speed; "Revoir" plays it again, "Ralenti" plays it again at half speed, once.
 */
final class Player {

    /** The speed of "Ralenti": slow enough to follow the hands. */
    private static final float SLOW = 0.5f;

    private final SignaireApplication app;
    private final SignVideoView video;
    private final TextView onlineOnly;
    /** The player of the video shown, once prepared: VideoView doesn't give it otherwise. */
    private @Nullable MediaPlayer prepared;
    /** Whether the play under way is the one "Ralenti" asked for, until it ends. */
    private boolean slow;

    Player(SignaireApplication app, SignVideoView video, TextView onlineOnly, View replay, View slowly) {
        this.app = app;
        this.video = video;
        this.onlineOnly = onlineOnly;
        MediaController controls = new MediaController(video.getContext());
        controls.setAnchorView(video);
        video.setMediaController(controls);
        video.setOnPreparedListener(player -> prepared = player);
        video.setOnCompletionListener(player -> slow = false);
        video.beforeStart(this::speed);
        replay.setOnClickListener(view -> replay(false));
        slowly.setOnClickListener(view -> replay(true));
    }

    void play(Media media) {
        prepared = null;
        slow = false;
        app.inBackground(() -> file(media), file -> {
            if (file.isPresent()) {
                onlineOnly.setVisibility(View.GONE);
                video.setVideoPath(file.get().toString());
            } else {
                onlineOnly.setVisibility(View.VISIBLE);
                video.setVideoURI(Uri.parse(media.sourceUrl()));
            }
            video.start();
        });
    }

    void pause() {
        video.pause();
    }

    void stop() {
        prepared = null;
        video.stopPlayback();
    }

    /** From the start: at half speed for "Ralenti", at the normal speed for "Revoir". */
    private void replay(boolean slowly) {
        slow = slowly;
        video.seekTo(0);
        video.start();
    }

    /**
     * Before each start: half speed for the play "Ralenti" asked for (resumed after a pause too), the normal speed for
     * any other.
     */
    private void speed() {
        MediaPlayer player = prepared;
        if (player == null) {
            // a video still to prepare, which starts at the normal speed
            return;
        }
        try {
            player.setPlaybackParams(player.getPlaybackParams().setSpeed(slow ? SLOW : 1f));
        } catch (IllegalStateException e) {
            // released by the VideoView (its surface gone): the next one starts at the normal speed
            prepared = null;
        }
    }

    /** Shows an image in a view, once read. */
    void show(Media media, ImageView view) {
        app.onNetwork(() -> image(media), image -> {
            if (image != null) {
                view.setImageBitmap(image);
            } else {
                view.setVisibility(View.GONE);
            }
        });
    }

    private Optional<Path> file(Media media) {
        try {
            return app.library().media(media, app.mediaCache());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @SuppressFBWarnings(value = "URLCONNECTION_SSRF_FD",
            justification = "The images of signs, at the addresses SignSuisse gave them")
    private @Nullable Bitmap image(Media media) {
        Optional<Path> file = file(media);
        if (file.isPresent()) {
            return BitmapFactory.decodeFile(file.get().toString());
        }
        try (InputStream in = URI.create(media.sourceUrl()).toURL().openStream()) {
            return BitmapFactory.decodeStream(in);
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }
}
