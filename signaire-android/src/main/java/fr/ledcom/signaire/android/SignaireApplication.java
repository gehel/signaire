package fr.ledcom.signaire.android;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import fr.ledcom.signaire.offline.SignLibrary;
import fr.ledcom.signaire.offline.SignSuisseDownload;
import fr.ledcom.signaire.offline.WordLists;

/**
 * What the screens share: the library of the bundles in the app's files, the word lists, and a thread for the work
 * that would hold the screen back. The bundles load on it when the app starts, before any search.
 */
public final class SignaireApplication extends Application {

    /** One thread: work runs in order, after the bundles are loaded. */
    private final ExecutorService background =
            Executors.newSingleThreadExecutor(work -> new Thread(work, "signaire-library"));
    /** Images from SignSuisse, which take their time. */
    private final ExecutorService network =
            Executors.newFixedThreadPool(2, work -> new Thread(work, "signaire-network"));
    private final Handler screen = new Handler(Looper.getMainLooper());
    private SignLibrary library;
    private WordLists lists;
    private final AtomicReference<Download> download = new AtomicReference<>(Download.IDLE);
    private final AtomicReference<@Nullable SignSuisseDownload> running = new AtomicReference<>();

    /**
     * Where a download is, for the library screen.
     *
     * @param message what to show, empty before the first download
     */
    record Download(boolean running, String message) {
        static final Download IDLE = new Download(false, "");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        library = new SignLibrary(getFilesDir().toPath().resolve("bundles"));
        lists = new WordLists(getFilesDir().toPath().resolve("lists.json"));
        background.execute(library::load);
    }

    /** Only in emulated processes: on phones, the threads end with the process. */
    @Override
    public void onTerminate() {
        background.shutdownNow();
        network.shutdownNow();
        super.onTerminate();
    }

    static SignaireApplication of(Context context) {
        return (SignaireApplication) context.getApplicationContext();
    }

    SignLibrary library() {
        return library;
    }

    WordLists lists() {
        return lists;
    }

    /** Media copied out of their bundle for the players. */
    Path mediaCache() {
        return getCacheDir().toPath().resolve("media");
    }

    /** Media downloaded from SignSuisse before they are written in their bundle. */
    Path downloadCache() {
        return getCacheDir().toPath().resolve("download");
    }

    /** Runs work on the library's thread, then gives its result to the screen. */
    <T> void inBackground(Supplier<T> work, Consumer<T> then) {
        background.execute(() -> {
            T result = work.get();
            screen.post(() -> then.accept(result));
        });
    }

    /** As {@link #inBackground}, for work on the network. */
    <T> void onNetwork(Supplier<T> work, Consumer<T> then) {
        network.execute(() -> {
            T result = work.get();
            screen.post(() -> then.accept(result));
        });
    }

    Download download() {
        return download.get();
    }

    void download(Download state) {
        download.set(state);
    }

    @Nullable SignSuisseDownload running() {
        return running.get();
    }

    void running(@Nullable SignSuisseDownload current) {
        running.set(current);
    }
}
