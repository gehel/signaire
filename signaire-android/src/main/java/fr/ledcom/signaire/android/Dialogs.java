package fr.ledcom.signaire.android;

import java.util.function.Consumer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;
import fr.ledcom.signaire.offline.WordListException;
import fr.ledcom.signaire.offline.WordLists;

/** The questions the screens ask: a name, a confirmation; and what went wrong with a list. */
final class Dialogs {

    private Dialogs() {
    }

    /** Asks for the name of a list. */
    static void askName(Activity activity, int title, String initial, Consumer<String> then) {
        EditText name = new EditText(activity);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setHint(R.string.lists_name);
        name.setText(initial);
        name.setSelection(initial.length());
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(name)
                .setPositiveButton(R.string.ok, (dialog, which) -> then.accept(name.getText().toString()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    static void confirm(Activity activity, String question, int action, Runnable then) {
        new AlertDialog.Builder(activity)
                .setMessage(question)
                .setPositiveButton(action, (dialog, which) -> then.run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Says why a change to a list was refused. */
    static void show(Context context, WordListException refused) {
        String message = switch (refused.reason()) {
            case INVALID_NAME -> context.getString(R.string.lists_error_INVALID_NAME, WordLists.MAX_NAME_LENGTH);
            case NAME_TAKEN -> context.getString(R.string.lists_error_NAME_TAKEN);
            case NOT_FOUND -> context.getString(R.string.lists_error_NOT_FOUND);
        };
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
