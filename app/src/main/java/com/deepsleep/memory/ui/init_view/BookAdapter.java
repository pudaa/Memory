package com.deepsleep.memory.ui.init_view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.deepsleep.memory.R;
import org.json.JSONException;
import org.json.JSONObject;
import com.bumptech.glide.Glide;
import java.util.List;

public class BookAdapter extends ArrayAdapter<JSONObject> {

    private Context context;
    private List<JSONObject> books;

    public BookAdapter(Context context, List<JSONObject> books) {
        super(context, 0, books);
        this.context = context;
        this.books = books;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.book_select_layout_item, parent, false);
        }

        JSONObject book = books.get(position);
        try {
            TextView bookTitle = convertView.findViewById(R.id.book_title);
            // TextView bookIntroduce = convertView.findViewById(R.id.book_introduce);
            TextView bookWordNum = convertView.findViewById(R.id.book_word_num);


            bookTitle.setText(book.getString("title"));
            // bookIntroduce.setText(book.getString("introduce"));
            bookWordNum.setText(book.getInt("wordNum")+"words");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return convertView;
    }
}