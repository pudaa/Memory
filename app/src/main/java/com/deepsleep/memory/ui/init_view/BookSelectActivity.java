package com.deepsleep.memory.ui.init_view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.deepsleep.memory.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.deepsleep.memory.handle_utils.lexicon.LexiconResourceMap.loadBooksFromJson;

public class BookSelectActivity extends AppCompatActivity {

    private List<JSONObject> allBooks;
    private List<JSONObject> filteredBooks;
    private ListView bookListView;
    private BookAdapter bookAdapter;
    private LinearLayout tagContainer;
    private Button lastSelectedButton;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.book_select_layout);
        EdgeToEdge.enable(this);
        bookListView = findViewById(R.id.book_list_view);
        tagContainer = findViewById(R.id.tag_container);

        allBooks = loadBooksFromJson(this);
        filteredBooks = new ArrayList<>(allBooks);

        // 生成标签按钮
        generateTagButtons();

        // 设置列表适配器
        bookAdapter = new BookAdapter(this, filteredBooks);
        bookListView.setAdapter(bookAdapter);

        // 设置列表项点击事件
        bookListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                JSONObject selectedBook = filteredBooks.get(position);
                try {
                    // Toast.makeText(BookSelectActivity.this, "选择了：" + selectedBook.getString("title"), Toast.LENGTH_SHORT).show();
                    // 跳转到计划制定页面，传递当前选择的书目的标题、单词数量等书籍信息
                    Intent intent = new Intent(BookSelectActivity.this, PlanDevelopmentActivity.class);
                    intent.putExtra("bookTitle", selectedBook.getString("title"));
                    intent.putExtra("bookWordCount", selectedBook.getInt("wordNum"));
                    intent.putExtra("bookId", selectedBook.getString("id"));
                    startActivity(intent);

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void generateTagButtons() {
        Set<String> tagSet = new HashSet<>();
        for (JSONObject book : allBooks) {
            try {
                JSONArray tags = book.getJSONArray("tags");
                for (int i = 0; i < tags.length(); i++) {
                    JSONObject tagObj = tags.getJSONObject(i);
                    String tagName = tagObj.getString("tagName");
                    tagSet.add(tagName);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        for (String tag : tagSet) {
            Button tagButton = new Button(this, null, 0, R.style.CustomTagButton);
            tagButton.setText(tag);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 0, 8, 0); // 设置上下左右边距
            tagButton.setLayoutParams(params);

            tagButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (lastSelectedButton != null) {
                        lastSelectedButton.setSelected(false);
                    }
                    tagButton.setSelected(true);
                    lastSelectedButton = (Button) v;
                    filterBooksByTag(tag);
                }
            });
            tagContainer.addView(tagButton);
        }
    }

    private void filterBooksByTag(String tag) {
        filteredBooks.clear();
        for (JSONObject book : allBooks) {
            try {
                JSONArray tags = book.getJSONArray("tags");
                for (int i = 0; i < tags.length(); i++) {
                    JSONObject tagObj = tags.getJSONObject(i);
                    String tagName = tagObj.getString("tagName");
                    if (tagName.equals(tag)) {
                        filteredBooks.add(book);
                        break;
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        bookAdapter.notifyDataSetChanged();
    }
}
