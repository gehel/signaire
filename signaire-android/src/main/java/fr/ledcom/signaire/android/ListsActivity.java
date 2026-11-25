package fr.ledcom.signaire.android;

import java.util.List;

import org.jspecify.annotations.Nullable;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import fr.ledcom.signaire.offline.WordList;
import fr.ledcom.signaire.offline.WordListException;

/** The word lists of the phone. */
public final class ListsActivity extends Activity {

    private SignaireApplication app;
    private List<WordList> lists = List.of();

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_list);
        setTitle(R.string.action_lists);
        app = SignaireApplication.of(this);
        this.<ListView>findViewById(R.id.items).setOnItemClickListener((parent, view, position, id) ->
                startActivity(ListActivity.intent(this, lists.get(position).id())));
    }

    @Override
    protected void onResume() {
        super.onResume();
        lists = app.lists().lists();
        List<String> names = lists.stream().map(list -> list.name() + " (" + list.signs().size() + ")").toList();
        this.<ListView>findViewById(R.id.items)
                .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
        TextView empty = findViewById(R.id.empty);
        empty.setText(R.string.lists_empty);
        empty.setVisibility(lists.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.lists, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.new_list) {
            Dialogs.askName(this, R.string.lists_new, "", name -> {
                try {
                    startActivity(ListActivity.intent(this, app.lists().create(name).id()));
                } catch (WordListException e) {
                    Dialogs.show(this, e);
                }
            });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
