package fr.ledcom.signaire.android;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import fr.ledcom.signaire.model.Sign;
import fr.ledcom.signaire.offline.WordList;
import fr.ledcom.signaire.offline.WordListException;

/** A word list: its signs, in the order they were added; a long press takes one out. */
public final class ListActivity extends Activity {

    private static final String LIST = "list";

    private SignaireApplication app;
    private SignAdapter signs;
    private long id;

    static Intent intent(Context context, long list) {
        return new Intent(context, ListActivity.class).putExtra(LIST, list);
    }

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_list);
        app = SignaireApplication.of(this);
        id = getIntent().getLongExtra(LIST, 0);
        signs = new SignAdapter(this);
        ListView items = findViewById(R.id.items);
        items.setAdapter(signs);
        items.setOnItemClickListener((parent, view, position, itemId) ->
                startActivity(SignActivity.intent(this, signs.getItem(position).uid())));
        items.setOnItemLongClickListener((parent, view, position, itemId) -> {
            Sign sign = signs.getItem(position);
            Dialogs.confirm(this, getString(R.string.list_remove_confirm, sign.name()), R.string.list_remove, () -> {
                app.lists().remove(id, sign.uid());
                refresh();
            });
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        Optional<WordList> list = app.lists().list(id);
        if (list.isEmpty()) {
            finish();
            return;
        }
        setTitle(list.get().name());
        app.inBackground(() -> app.library().signs(list.get().signs()), found -> {
            signs.show(found);
            TextView empty = findViewById(R.id.empty);
            empty.setText(R.string.list_empty);
            empty.setVisibility(found.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Optional<WordList> list = app.lists().list(id);
        if (list.isEmpty()) {
            return super.onOptionsItemSelected(item);
        }
        if (item.getItemId() == R.id.rename) {
            Dialogs.askName(this, R.string.lists_rename, list.get().name(), name -> {
                try {
                    app.lists().rename(id, name);
                    refresh();
                } catch (WordListException e) {
                    Dialogs.show(this, e);
                }
            });
            return true;
        }
        if (item.getItemId() == R.id.delete) {
            Dialogs.confirm(this, getString(R.string.lists_delete_confirm, list.get().name()), R.string.lists_delete,
                    () -> {
                        app.lists().delete(id);
                        finish();
                    });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
