package fr.ledcom.signaire.android;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.ledcom.signaire.model.Media;

/**
 * Videos and images of a sign: from their bundle when they were downloaded, from SignSuisse otherwise. Videos loop:
 * a sign is seen several times before it is understood.
 */
final class Player {

    private final SignaireApplication app;
    private final VideoView video;
    private final TextView onlineOnly;

    Player(SignaireApplication app, VideoView video, TextView onlineOnly) {
        this.app = app;
        this.video = video;
        this.onlineOnly = onlineOnly;
        MediaController controls = new MediaController(video.getContext());
        controls.setAnchorView(video);
        video.setMediaController(controls);
        video.setOnPreparedListener(player -> player.setLooping(true));
    }

    void play(Media media) {
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

    void stop() {
        video.stopPlayback();
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
