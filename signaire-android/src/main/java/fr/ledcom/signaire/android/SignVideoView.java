package fr.ledcom.signaire.android;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

/**
 * A video view that runs an action before each start, whoever asks for it: "Revoir", "Ralenti", or the play button of
 * {@code MediaController}. The player sets the speed there: {@code MediaPlayer} keeps the last one otherwise.
 */
public final class SignVideoView extends VideoView {

    private Runnable beforeStart = () -> {
        // nothing until the player says
    };

    public SignVideoView(Context context, AttributeSet attributes) {
        super(context, attributes);
    }

    void beforeStart(Runnable action) {
        beforeStart = action;
    }

    @Override
    public void start() {
        beforeStart.run();
        super.start();
    }
}
