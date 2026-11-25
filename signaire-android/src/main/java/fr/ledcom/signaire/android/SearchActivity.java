package fr.ledcom.signaire.android;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;
import fr.ledcom.signaire.offline.Query;

/** The first screen: the search, as the home page of the web application. */
public final class SearchActivity extends Activity {

    /** Searches wait for a pause in the typing. */
    private static final long TYPING_PAUSE_MILLIS = 250;

    private final Handler screen = new Handler(Looper.getMainLooper());
    private final Runnable searchNow = this::search;
    private SignaireApplication app;
    private SignAdapter results;
    private EditText words;
    private Spinner language;
    private Spinner category;
    private TextView status;
    private Button openLibrary;
    private List<Category> categories;
    /** The latest search: the results of earlier ones are dropped. */
    private int searches;

    /** What a search found, and whether there are signs at all. */
    private record Found(List<Sign> signs, boolean libraryEmpty) {
    }

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_search);
        app = SignaireApplication.of(this);
        results = new SignAdapter(this);
        words = findViewById(R.id.search);
        language = findViewById(R.id.language);
        category = findViewById(R.id.category);
        status = findViewById(R.id.status);
        openLibrary = findViewById(R.id.open_library);
        ListView list = findViewById(R.id.results);
        list.setAdapter(results);
        list.setOnItemClickListener((parent, view, position, id) ->
                startActivity(SignActivity.intent(this, results.getItem(position).uid())));
        openLibrary.setOnClickListener(view -> startActivity(new Intent(this, LibraryActivity.class)));

        List<String> languages = new ArrayList<>(Language.values().length + 1);
        languages.add(getString(R.string.search_all_languages));
        for (Language each : Language.values()) {
            languages.add(Labels.signLanguage(this, each));
        }
        language.setAdapter(choices(languages));
        categories = Labels.categoriesByName(this);
        List<String> categoryNames = new ArrayList<>(categories.size() + 1);
        categoryNames.add(getString(R.string.search_all_categories));
        categories.forEach(each -> categoryNames.add(Labels.category(this, each)));
        category.setAdapter(choices(categoryNames));

        words.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                // only the text after the change matters
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                // only the text after the change matters
            }

            @Override
            public void afterTextChanged(Editable text) {
                screen.removeCallbacks(searchNow);
                screen.postDelayed(searchNow, TYPING_PAUSE_MILLIS);
            }
        });
        AdapterView.OnItemSelectedListener filter = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                search();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                search();
            }
        };
        language.setOnItemSelectedListener(filter);
        category.setOnItemSelectedListener(filter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // signs may have been downloaded or imported meanwhile
        search();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.lists) {
            startActivity(new Intent(this, ListsActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.library) {
            startActivity(new Intent(this, LibraryActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void search() {
        int languageChoice = language.getSelectedItemPosition();
        int categoryChoice = category.getSelectedItemPosition();
        Query query = new Query(words.getText().toString(),
                languageChoice > 0 ? Set.of(Language.values()[languageChoice - 1]) : Set.of(),
                categoryChoice > 0 ? Set.of(categories.get(categoryChoice - 1)) : Set.of());
        int search = ++searches;
        app.inBackground(() -> new Found(app.library().search(query), app.library().bundles().isEmpty()), found -> {
            if (search == searches) {
                show(found);
            }
        });
    }

    private void show(Found found) {
        results.show(found.signs());
        openLibrary.setVisibility(found.libraryEmpty() ? View.VISIBLE : View.GONE);
        if (found.libraryEmpty()) {
            status.setText(R.string.library_empty);
        } else if (found.signs().isEmpty()) {
            status.setText(R.string.search_none);
        } else {
            int count = found.signs().size();
            status.setText(getResources().getQuantityString(R.plurals.search_count, count, count));
        }
    }

    private ArrayAdapter<String> choices(List<String> names) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }
}
