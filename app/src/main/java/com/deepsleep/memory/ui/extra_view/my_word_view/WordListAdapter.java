package com.deepsleep.memory.ui.extra_view.my_word_view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.deepsleep.memory.R;
import com.deepsleep.memory.handle_utils.AudioPlayer;
import com.deepsleep.memory.handle_utils.lexicon.WordEntry;

import java.util.List;

public class WordListAdapter extends RecyclerView.Adapter<WordListAdapter.WordViewHolder> {
    private final List<WordEntry> wordList;


    public WordListAdapter(List<WordEntry> wordList) {
        this.wordList = wordList;
    }

    @NonNull
    @Override
    public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.fragment_word_list_item, parent, false);
        return new WordViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
        WordEntry entry = wordList.get(position);

        holder.tvWord.setText(entry.getHeadWord());
        holder.tvPhoneticUS.setText(String.format("美音:%s ", entry.getUsPhone()));
        holder.tvPhoneticUS.setOnClickListener(v -> {
            AudioPlayer.playAudio(v.getContext(), entry.getHeadWord(), true);
        });
        holder.tvPhoneticUK.setText(String.format("英音:%s ", entry.getUkPhone()));
        holder.tvPhoneticUK.setOnClickListener(v -> {
            AudioPlayer.playAudio(v.getContext(), entry.getHeadWord(), false);
        });
        holder.tvDefinition.setText(entry.getChineseTranslation());

        boolean isExpanded = false;

        holder.itemView.setOnClickListener(v -> {
            boolean visible = holder.definitionContainer.getVisibility() == View.VISIBLE;
            holder.definitionContainer.setVisibility(visible ? View.INVISIBLE : View.VISIBLE);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onItemLongClick(entry);
            }
            return true;
        });

    }


    @Override
    public int getItemCount() {
        return wordList.size();
    }

    public void removeItem(String headWord) {
        for (int i = 0; i < wordList.size(); i++) {
            if (wordList.get(i).getHeadWord().equals(headWord)) {
                wordList.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    static class WordViewHolder extends RecyclerView.ViewHolder {
        TextView tvWord, tvPhoneticUS, tvPhoneticUK, tvDefinition;
        LinearLayout definitionContainer;

        public WordViewHolder(@NonNull View itemView) {
            super(itemView);
            tvWord = itemView.findViewById(R.id.tv_word);
            tvPhoneticUS = itemView.findViewById(R.id.tv_phonetic_US);
            tvPhoneticUK = itemView.findViewById(R.id.tv_phonetic_UK);
            tvDefinition = itemView.findViewById(R.id.tv_definition);
            definitionContainer = itemView.findViewById(R.id.definition_container);
        }
    }
    public interface OnItemLongClickListener {
        void onItemLongClick(WordEntry wordEntry);
    }

    private OnItemLongClickListener listener;

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.listener = listener;
    }
}
