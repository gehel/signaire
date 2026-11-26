package fr.ledcom.signaire.android;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import fr.ledcom.signaire.model.DialectVariant;
import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;
import fr.ledcom.signaire.offline.WordList;
import fr.ledcom.signaire.offline.WordListException;

/**
 * A sign: its gloss, its video, its texts, its examples; and the lists it is in. The videos of the examples play in a
 * player of their own, so that the sign's stays.
 */
public final class SignActivity extends Activity {

    private static final String UID = "uid";
    private static final int IMAGE_HEIGHT_DP = 160;

    private SignaireApplication app;
    private Player player;
    private View examplePlayerView;
    private Player examplePlayer;
    private @Nullable Sign sign;

    static Intent intent(Context context, String uid) {
        return new Intent(context, SignActivity.class).putExtra(UID, uid);
    }

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_sign);
        app = SignaireApplication.of(this);
        player = new Player(app, findViewById(R.id.video), findViewById(R.id.online_only));
        examplePlayerView = getLayoutInflater().inflate(R.layout.example_player, findViewById(R.id.examples), false);
        examplePlayer = new Player(app, examplePlayerView.findViewById(R.id.example_video),
                examplePlayerView.findViewById(R.id.example_online_only));
        String uid = String.valueOf(getIntent().getStringExtra(UID));
        app.inBackground(() -> app.library().sign(uid), found -> {
            if (found.isPresent()) {
                show(found.get());
            } else {
                finish();
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        player.stop();
        examplePlayer.stop();
    }

    private void show(Sign shown) {
        sign = shown;
        setTitle(shown.name());
        this.<TextView>findViewById(R.id.name).setText(shown.name());
        this.<TextView>findViewById(R.id.language).setText(Labels.signLanguage(this, shown.language()));
        if (!shown.videos().isEmpty()) {
            player.play(shown.videos().get(0));
        }
        LinearLayout images = findViewById(R.id.images);
        int height = Math.round(IMAGE_HEIGHT_DP * getResources().getDisplayMetrics().density);
        for (Media image : shown.images()) {
            ImageView view = new ImageView(this);
            view.setAdjustViewBounds(true);
            view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height));
            images.addView(view);
            player.show(image, view);
        }
        this.<TextView>findViewById(R.id.texts).setText(texts(shown));
        showExamples(shown);
        TextView categories = findViewById(R.id.categories);
        categories.setText(getString(R.string.sign_categories, shown.categories().stream()
                .map(category -> Labels.category(this, category)).collect(Collectors.joining(", "))));
        categories.setVisibility(shown.categories().isEmpty() ? View.GONE : View.VISIBLE);
        showLists();
        findViewById(R.id.add_to_list).setOnClickListener(view -> chooseList());
        String source = shown.sourceUrl();
        View link = findViewById(R.id.source);
        link.setVisibility(source == null ? View.GONE : View.VISIBLE);
        if (source != null) {
            link.setOnClickListener(view -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(source))));
        }
    }

    /** The periphrase and the definition, under their labels. */
    private CharSequence texts(Sign shown) {
        SpannableStringBuilder texts = new SpannableStringBuilder();
        labelled(texts, R.string.sign_periphrase, shown.periphrase());
        labelled(texts, R.string.sign_definition, shown.definition());
        return texts;
    }

    private void labelled(SpannableStringBuilder texts, int label, @Nullable String text) {
        if (text == null) {
            return;
        }
        if (texts.length() > 0) {
            texts.append("\n\n");
        }
        int start = texts.length();
        texts.append(getString(label)).append('\n');
        texts.setSpan(new StyleSpan(Typeface.BOLD), start, texts.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        texts.append(text);
    }

    /**
     * The examples and the regional variants: a line each, which plays its video, when it has one, under the heading
     * of its section.
     */
    private void showExamples(Sign shown) {
        LinearLayout examples = findViewById(R.id.examples);
        if (!shown.examples().isEmpty()) {
            TextView heading = heading(R.string.sign_examples);
            examples.addView(heading);
            for (Example example : shown.examples()) {
                String text = example.text();
                examples.addView(line(text == null ? getString(R.string.sign_play) : text, example.video(), heading));
            }
        }
        if (!shown.dialectVariants().isEmpty()) {
            TextView heading = heading(R.string.sign_variants);
            examples.addView(heading);
            for (DialectVariant variant : shown.dialectVariants()) {
                examples.addView(line(String.join(", ", variant.dialects()), variant.video(), heading));
            }
        }
    }

    /** Plays the video of an example or a variant under the heading of its section, the sign's paused. */
    private void playExample(Media video, View heading) {
        LinearLayout examples = findViewById(R.id.examples);
        examples.removeView(examplePlayerView);
        examples.addView(examplePlayerView, examples.indexOfChild(heading) + 1);
        player.pause();
        examplePlayer.play(video);
        examplePlayerView.post(() -> examplePlayerView.requestRectangleOnScreen(
                new Rect(0, 0, examplePlayerView.getWidth(), examplePlayerView.getHeight())));
    }

    private TextView heading(int label) {
        TextView heading = new TextView(this);
        heading.setText(label);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setPadding(0, padding(), 0, 0);
        return heading;
    }

    private TextView line(String text, @Nullable Media video, View heading) {
        TextView line = new TextView(this);
        line.setPadding(0, padding() / 2, 0, padding() / 2);
        if (video == null) {
            line.setText(text);
        } else {
            line.setText("▶︎ " + text);
            line.setContentDescription(getString(R.string.sign_play) + " : " + text);
            line.setOnClickListener(view -> playExample(video, heading));
        }
        return line;
    }

    private int padding() {
        return Math.round(8 * getResources().getDisplayMetrics().density);
    }

    private void showLists() {
        Sign shown = sign;
        if (shown == null) {
            return;
        }
        List<WordList> lists = app.lists().listsWith(shown.uid());
        TextView in = findViewById(R.id.lists);
        in.setText(getString(R.string.sign_in_lists,
                lists.stream().map(WordList::name).collect(Collectors.joining(", "))));
        in.setVisibility(lists.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** The lists, and a new one at the end. */
    private void chooseList() {
        List<WordList> lists = app.lists().lists();
        List<String> names = new ArrayList<>();
        lists.forEach(list -> names.add(list.name()));
        names.add(getString(R.string.lists_new));
        new AlertDialog.Builder(this)
                .setTitle(R.string.sign_add_to_list)
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    if (which < lists.size()) {
                        add(lists.get(which).id());
                    } else {
                        Dialogs.askName(this, R.string.lists_new, "", this::addToNew);
                    }
                })
                .show();
    }

    private void addToNew(String name) {
        try {
            add(app.lists().create(name).id());
        } catch (WordListException e) {
            Dialogs.show(this, e);
        }
    }

    private void add(long list) {
        Sign shown = sign;
        if (shown == null) {
            return;
        }
        try {
            WordList added = app.lists().add(list, shown.uid());
            Toast.makeText(this, getString(R.string.sign_added, added.name()), Toast.LENGTH_SHORT).show();
            showLists();
        } catch (WordListException e) {
            Dialogs.show(this, e);
        }
    }
}
