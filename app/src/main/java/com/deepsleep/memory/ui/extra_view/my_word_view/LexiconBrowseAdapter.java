package com.deepsleep.memory.ui.extra_view.my_word_view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.deepsleep.memory.R;
import com.deepsleep.memory.handle_utils.AudioPlayer;
import com.deepsleep.memory.handle_utils.lexicon.WordEntry;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 词书浏览适配器 —— 支持展开/折叠查看单词详情
 */
public class LexiconBrowseAdapter extends RecyclerView.Adapter<LexiconBrowseAdapter.ViewHolder> {

    private final List<WordEntry> wordList;
    private final Set<Integer> expandedPositions = new HashSet<>();

    public LexiconBrowseAdapter(List<WordEntry> wordList) {
        this.wordList = wordList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lexicon_browse_word, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WordEntry entry = wordList.get(position);
        boolean expanded = expandedPositions.contains(position);

        holder.tvWord.setText(entry.getHeadWord());

        // 词性
        String pos = entry.getPos();
        holder.tvPos.setText(pos.isEmpty() ? "" : pos + ".");
        holder.tvPos.setVisibility(pos.isEmpty() ? View.GONE : View.VISIBLE);

        // 序号
        holder.tvRank.setText("#" + entry.getWordRank());

        // 美式发音
        String us = entry.getUsPhone();
        if (!us.isEmpty()) {
            holder.tvPhoneticUS.setText("美 /" + us + "/");
            holder.tvPhoneticUS.setVisibility(View.VISIBLE);
            holder.btnSpeakerUS.setVisibility(View.VISIBLE);
            holder.btnSpeakerUS
                    .setOnClickListener(v -> AudioPlayer.playAudio(v.getContext(), entry.getHeadWord(), true));
        } else {
            holder.tvPhoneticUS.setVisibility(View.GONE);
            holder.btnSpeakerUS.setVisibility(View.GONE);
        }

        // 英式发音
        String uk = entry.getUkPhone();
        if (!uk.isEmpty()) {
            holder.tvPhoneticUK.setText("英 /" + uk + "/");
            holder.tvPhoneticUK.setVisibility(View.VISIBLE);
            holder.btnSpeakerUK.setVisibility(View.VISIBLE);
            holder.btnSpeakerUK
                    .setOnClickListener(v -> AudioPlayer.playAudio(v.getContext(), entry.getHeadWord(), false));
        } else {
            holder.tvPhoneticUK.setVisibility(View.GONE);
            holder.btnSpeakerUK.setVisibility(View.GONE);
        }

        // 隐藏发音行如果没有任何音标
        boolean hasPhonetic = !us.isEmpty() || !uk.isEmpty();
        holder.phoneticRow.setVisibility(hasPhonetic ? View.VISIBLE : View.GONE);

        // 中文释义
        holder.tvDefinition.setText(entry.getChineseTranslation());

        // ========== 展开区域 ==========
        if (expanded) {
            holder.expandArea.setVisibility(View.VISIBLE);

            // 英英释义
            String engDef = entry.getEnglishDefinition();
            if (!engDef.isEmpty()) {
                holder.tvEngDef.setText(engDef);
                holder.tvEngDef.setVisibility(View.VISIBLE);
            } else {
                holder.tvEngDef.setVisibility(View.GONE);
            }

            // 例句
            List<WordEntry.ExampleSentence> examples = entry.getExampleSentences();
            holder.exampleContainer.removeAllViews();
            if (!examples.isEmpty()) {
                holder.tvSectionExample.setVisibility(View.VISIBLE);
                holder.exampleContainer.setVisibility(View.VISIBLE);
                for (int i = 0; i < Math.min(examples.size(), 3); i++) {
                    addSentenceView(holder.exampleContainer, examples.get(i).getEn(), examples.get(i).getCn(),
                            R.color.theme_text_secondary);
                }
            } else {
                holder.tvSectionExample.setVisibility(View.GONE);
                holder.exampleContainer.setVisibility(View.GONE);
            }

            // 真题例句
            List<WordEntry.RealExamSentence> realExams = entry.getRealExamSentences();
            holder.realExamContainer.removeAllViews();
            if (!realExams.isEmpty()) {
                holder.tvSectionReal.setVisibility(View.VISIBLE);
                holder.realExamContainer.setVisibility(View.VISIBLE);
                for (int i = 0; i < Math.min(realExams.size(), 2); i++) {
                    WordEntry.RealExamSentence re = realExams.get(i);
                    addRealExamView(holder.realExamContainer, re.getContent(), re.getSourceLabel());
                }
            } else {
                holder.tvSectionReal.setVisibility(View.GONE);
                holder.realExamContainer.setVisibility(View.GONE);
            }
        } else {
            holder.expandArea.setVisibility(View.GONE);
        }

        // 点击切换展开/折叠
        holder.itemView.setOnClickListener(v -> {
            if (expandedPositions.contains(position)) {
                expandedPositions.remove(position);
            } else {
                expandedPositions.add(position);
            }
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return wordList.size();
    }

    // ========== 动态添加句子视图 ==========

    private void addSentenceView(LinearLayout container, String en, String cn, int cnColor) {
        View view = LayoutInflater.from(container.getContext()).inflate(R.layout.item_sentence_line, container, false);
        TextView tvEn = view.findViewById(R.id.tv_sentence_en);
        TextView tvCn = view.findViewById(R.id.tv_sentence_cn);
        tvEn.setText(en);
        tvCn.setText(cn);
        tvCn.setTextColor(container.getContext().getResources().getColor(cnColor));
        container.addView(view);
    }

    private void addRealExamView(LinearLayout container, String content, String source) {
        View view = LayoutInflater.from(container.getContext()).inflate(R.layout.item_real_exam_line, container, false);
        TextView tvContent = view.findViewById(R.id.tv_real_content);
        TextView tvSource = view.findViewById(R.id.tv_real_source);
        tvContent.setText(content);
        tvSource.setText(source);
        container.addView(view);
    }

    // ========== ViewHolder ==========

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvWord, tvPos, tvRank;
        LinearLayout phoneticRow;
        ImageView btnSpeakerUS, btnSpeakerUK;
        TextView tvPhoneticUS, tvPhoneticUK;
        TextView tvDefinition;
        LinearLayout expandArea;
        TextView tvEngDef;
        TextView tvSectionExample;
        LinearLayout exampleContainer;
        TextView tvSectionReal;
        LinearLayout realExamContainer;

        ViewHolder(View v) {
            super(v);
            tvWord = v.findViewById(R.id.tv_word);
            tvPos = v.findViewById(R.id.tv_pos);
            tvRank = v.findViewById(R.id.tv_rank);
            phoneticRow = v.findViewById(R.id.phonetic_row);
            btnSpeakerUS = v.findViewById(R.id.btn_speaker_us);
            btnSpeakerUK = v.findViewById(R.id.btn_speaker_uk);
            tvPhoneticUS = v.findViewById(R.id.tv_phonetic_us);
            tvPhoneticUK = v.findViewById(R.id.tv_phonetic_uk);
            tvDefinition = v.findViewById(R.id.tv_definition);
            expandArea = v.findViewById(R.id.expand_area);
            tvEngDef = v.findViewById(R.id.tv_eng_def);
            tvSectionExample = v.findViewById(R.id.tv_section_example);
            exampleContainer = v.findViewById(R.id.example_container);
            tvSectionReal = v.findViewById(R.id.tv_section_real);
            realExamContainer = v.findViewById(R.id.real_exam_container);
        }
    }
}
