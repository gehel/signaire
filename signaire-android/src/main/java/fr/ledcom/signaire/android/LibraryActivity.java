package fr.ledcom.signaire.android;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;
import fr.ledcom.signaire.bundle.BundleException;
import fr.ledcom.signaire.extractor.MediaChoice;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.offline.BundleInfo;

/** The signs of the phone: a bundle per category; downloads from SignSuisse and imports of bundle files. */
public final class LibraryActivity extends Activity {

    private static final int IMPORT = 1;
    private static final long PROGRESS_MILLIS = 1000;
    private static final String TITLE = "title";
    private static final String DETAIL = "detail";
    private static final MediaChoice[] MEDIA = {MediaChoice.ALL, MediaChoice.VIDEOS, MediaChoice.IMAGES,
        MediaChoice.NONE};

    private final Handler screen = new Handler(Looper.getMainLooper());
    private final Runnable followDownload = this::followDownload;
    private SignaireApplication app;
    private boolean wasRunning;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_library);
        setTitle(R.string.action_library);
        app = SignaireApplication.of(this);
        findViewById(R.id.download).setOnClickListener(view -> chooseCategories());
        findViewById(R.id.import_bundle).setOnClickListener(view -> startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                        .putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/zip",
                            "application/octet-stream"}), IMPORT));
        findViewById(R.id.cancel).setOnClickListener(view -> DownloadService.cancel(app));
        if (app.testServer()) {
            TextView server = findViewById(R.id.server);
            server.setText(getString(R.string.library_server, app.signSuisse()));
            server.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        showBundles();
        followDownload();
    }

    @Override
    protected void onPause() {
        super.onPause();
        screen.removeCallbacks(followDownload);
    }

    @Override
    protected void onActivityResult(int request, int result, @Nullable Intent data) {
        super.onActivityResult(request, result, data);
        Uri file = data == null ? null : data.getData();
        if (request != IMPORT || result != RESULT_OK || file == null) {
            return;
        }
        app.inBackground(() -> importBundle(file), imported -> {
            Toast.makeText(this, imported.map(bundle -> getString(R.string.library_imported,
                    Labels.category(this, bundle.category())))
                    .orElseGet(() -> getString(R.string.library_import_failed)), Toast.LENGTH_LONG).show();
            showBundles();
        });
    }

    private Optional<BundleInfo> importBundle(Uri file) {
        try (InputStream in = getContentResolver().openInputStream(file)) {
            return in == null ? Optional.empty() : Optional.of(app.library().importBundle(in));
        } catch (IOException | BundleException e) {
            return Optional.empty();
        }
    }

    private void showBundles() {
        DateTimeFormatter dates = new DateTimeFormatterBuilder().appendLocalized(FormatStyle.MEDIUM, null)
                .toFormatter(Labels.locale(this)).withZone(ZoneId.systemDefault());
        app.inBackground(() -> app.library().bundles(), bundles -> {
            List<Map<String, String>> rows = new ArrayList<>(bundles.size());
            for (BundleInfo bundle : bundles) {
                rows.add(Map.of(TITLE, Labels.category(this, bundle.category()), DETAIL, getString(
                        R.string.library_bundle, bundle.signCount(), dates.format(bundle.createdAt()))));
            }
            this.<ListView>findViewById(R.id.bundles).setAdapter(new SimpleAdapter(this, rows,
                    android.R.layout.simple_list_item_2, new String[] {TITLE, DETAIL},
                    new int[] {android.R.id.text1, android.R.id.text2}));
        });
    }

    /** Shows where a download is, and the signs it added when it ends. */
    private void followDownload() {
        SignaireApplication.Download download = app.download();
        TextView progress = findViewById(R.id.progress);
        progress.setText(download.message());
        progress.setVisibility(download.message().isEmpty() ? View.GONE : View.VISIBLE);
        findViewById(R.id.cancel).setVisibility(download.running() ? View.VISIBLE : View.GONE);
        findViewById(R.id.download).setEnabled(!download.running());
        if (wasRunning && !download.running()) {
            showBundles();
        }
        wasRunning = download.running();
        screen.postDelayed(followDownload, PROGRESS_MILLIS);
    }

    private void chooseCategories() {
        List<Category> categories = Labels.categoriesByName(this);
        String[] names = categories.stream().map(category -> Labels.category(this, category)).toArray(String[]::new);
        boolean[] checked = new boolean[names.length];
        new AlertDialog.Builder(this)
                .setTitle(R.string.download_categories)
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    Set<Category> chosen = EnumSet.noneOf(Category.class);
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            chosen.add(categories.get(i));
                        }
                    }
                    if (!chosen.isEmpty()) {
                        chooseLanguages(chosen);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void chooseLanguages(Set<Category> categories) {
        Language[] languages = Language.values();
        String[] names = new String[languages.length];
        boolean[] checked = new boolean[languages.length];
        Language ofThePhone = Labels.signLanguageOfThePhone(this);
        for (int i = 0; i < languages.length; i++) {
            names[i] = Labels.signLanguage(this, languages[i]);
            checked[i] = languages[i] == ofThePhone;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.download_languages)
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    Set<Language> chosen = EnumSet.noneOf(Language.class);
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            chosen.add(languages[i]);
                        }
                    }
                    if (!chosen.isEmpty()) {
                        chooseMedia(categories, chosen);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void chooseMedia(Set<Category> categories, Set<Language> languages) {
        String[] names = new String[MEDIA.length];
        for (int i = 0; i < MEDIA.length; i++) {
            names[i] = getString(getResources().getIdentifier("download_media_" + MEDIA[i].name(), "string",
                    getPackageName()));
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.download_media)
                .setItems(names, (dialog, which) -> {
                    // the notification of the download, when the phone allows it
                    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 0);
                    }
                    DownloadService.start(this, categories, languages, MEDIA[which]);
                    followDownload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
