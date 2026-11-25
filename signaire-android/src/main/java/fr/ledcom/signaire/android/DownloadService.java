package fr.ledcom.signaire.android;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

import org.jspecify.annotations.Nullable;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import fr.ledcom.signaire.bundle.BundleException;
import fr.ledcom.signaire.extractor.ExtractionException;
import fr.ledcom.signaire.extractor.ExtractionListener;
import fr.ledcom.signaire.extractor.ExtractionOptions;
import fr.ledcom.signaire.extractor.ExtractionReport;
import fr.ledcom.signaire.extractor.ExtractionStatus;
import fr.ledcom.signaire.extractor.MediaChoice;
import fr.ledcom.signaire.extractor.UserAgent;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.offline.SignSuisseDownload;

/**
 * Downloads categories from SignSuisse, with a notification: Android lets a foreground service work while the app is
 * in the background. One download at a time; where it is, the application knows.
 */
public final class DownloadService extends Service {

    private static final String CHANNEL = "downloads";
    private static final int NOTIFICATION = 1;
    private static final String CATEGORIES = "categories";
    private static final String LANGUAGES = "languages";
    private static final String MEDIA = "media";

    static void start(Context context, Collection<Category> categories, Collection<Language> languages,
            MediaChoice media) {
        context.startForegroundService(new Intent(context, DownloadService.class)
                .putExtra(CATEGORIES, categories.stream().map(Enum::name).toArray(String[]::new))
                .putExtra(LANGUAGES, languages.stream().map(Enum::name).toArray(String[]::new))
                .putExtra(MEDIA, media.name()));
    }

    static void cancel(SignaireApplication app) {
        SignSuisseDownload running = app.running();
        if (running != null) {
            running.cancel();
        }
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        createChannel();
        String starting = getString(R.string.download_starting);
        startForeground(NOTIFICATION, notification(starting, true), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        SignaireApplication app = SignaireApplication.of(this);
        if (intent == null || app.running() != null) {
            return START_NOT_STICKY;
        }
        Set<Category> categories = EnumSet.noneOf(Category.class);
        for (String name : names(intent.getStringArrayExtra(CATEGORIES))) {
            categories.add(Category.valueOf(name));
        }
        Set<Language> languages = EnumSet.noneOf(Language.class);
        for (String name : names(intent.getStringArrayExtra(LANGUAGES))) {
            languages.add(Language.valueOf(name));
        }
        MediaChoice media = MediaChoice.valueOf(String.valueOf(intent.getStringExtra(MEDIA)));
        SignSuisseDownload download = SignSuisseDownload.fromSignSuisse(app.library(), app.downloadCache(),
                UserAgent.of("", ""), new Progress());
        app.running(download);
        app.download(new SignaireApplication.Download(true, starting));
        new Thread(() -> run(download, new ArrayList<>(categories), languages, ExtractionOptions.of(media)),
                "signaire-download").start();
        return START_NOT_STICKY;
    }

    @Override
    public @Nullable IBinder onBind(Intent intent) {
        return null;
    }

    private void run(SignSuisseDownload download, List<Category> categories, Set<Language> languages,
            ExtractionOptions options) {
        SignaireApplication app = SignaireApplication.of(this);
        String result;
        try {
            int signs = download.download(categories, languages, options).stream()
                    .mapToInt(ExtractionReport::signCount).sum();
            result = getString(R.string.download_done, signs);
        } catch (CancellationException e) {
            result = getString(R.string.download_cancelled);
        } catch (ExtractionException | BundleException | UncheckedIOException e) {
            result = getString(R.string.download_failed, e.getMessage());
        } finally {
            app.running(null);
        }
        app.download(new SignaireApplication.Download(false, result));
        stopForeground(STOP_FOREGROUND_DETACH);
        getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(result, false));
        stopSelf();
    }

    /** Where the download is, in the notification and on the library screen. */
    private final class Progress implements ExtractionListener {
        @Override
        public void progress(ExtractionStatus status) {
            String message = getString(R.string.download_running, Labels.category(DownloadService.this,
                    status.category()), status.signs().read() + status.signs().reused(), status.signs().found(),
                    status.media().downloaded());
            SignaireApplication.of(DownloadService.this).download(new SignaireApplication.Download(true, message));
            getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(message, true));
        }
    }

    private void createChannel() {
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,
                getString(R.string.download_channel), NotificationManager.IMPORTANCE_LOW));
    }

    private Notification notification(String text, boolean ongoing) {
        PendingIntent library = PendingIntent.getActivity(this, 0, new Intent(this, LibraryActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(getString(R.string.library_download))
                .setContentText(text)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .setContentIntent(library)
                .build();
    }

    private static String[] names(String @Nullable [] names) {
        return names == null ? new String[0] : names;
    }
}
