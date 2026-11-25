package fr.ledcom.signaire.android;

import java.util.List;

import org.jspecify.annotations.Nullable;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import fr.ledcom.signaire.model.Sign;

/** Signs as entries of a lexicon: the gloss, its sign language, the start of its definition. */
final class SignAdapter extends BaseAdapter {

    private final Context context;
    private final LayoutInflater inflater;
    private List<Sign> signs = List.of();

    SignAdapter(Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
    }

    void show(List<Sign> shown) {
        signs = List.copyOf(shown);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return signs.size();
    }

    @Override
    public Sign getItem(int position) {
        return signs.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, @Nullable View convertView, ViewGroup parent) {
        View view = convertView != null ? convertView : inflater.inflate(R.layout.item_sign, parent, false);
        Sign sign = getItem(position);
        TextView name = view.findViewById(R.id.name);
        name.setText(sign.name());
        TextView language = view.findViewById(R.id.language);
        language.setText(Labels.signLanguageShort(context, sign.language()));
        String definition = sign.definition();
        String text = definition != null ? definition : sign.periphrase();
        TextView description = view.findViewById(R.id.definition);
        description.setText(text);
        description.setVisibility(text == null ? View.GONE : View.VISIBLE);
        return view;
    }
}
