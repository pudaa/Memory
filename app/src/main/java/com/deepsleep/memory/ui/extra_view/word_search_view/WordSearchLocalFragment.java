package com.deepsleep.memory.ui.extra_view.word_search_view;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.deepsleep.memory.R;
import com.deepsleep.memory.handle_utils.AudioPlayer;
import com.deepsleep.memory.handle_utils.lexicon.LexiconResourceMap;
import com.deepsleep.memory.handle_utils.lexicon.WordEntry;
import com.deepsleep.memory.handle_utils.lexicon.WordFormNormalizer;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 本地词书查词结果页。
 *
 * <p>
 * 设计要点：
 * <ol>
 * <li><b>计划词书优先</b>：同一单词常被多本词书收录，优先呈现当前学习计划词书中的条目
 * （{@link LexiconResourceMap#getPreferredLexiconId}），该词书未收录时才跨书取信息最丰富的条目，
 * 并在底部标注来源词书；此时提供「切换词书」入口，用户可自行选择展示哪一本；</li>
 * <li><b>全字段呈现</b>：音标、词性、中文释义、英文释义、例句、真题例句、同近义词
 * 都来自本地 Room 库；</li>
 * <li><b>长板块先给预览</b>：例句与真题例句默认各展示前 {@value #PREVIEW_COUNT} 条，
 * 超出部分折叠为「展开全部 N 条」，避免大量例句占满滚动视图；</li>
 * <li><b>板块按需渲染</b>：每个板块（{@code word_result_section}）默认 GONE，只有词书确实
 * 提供了该字段才显示，避免「没有例句却渲染例句小标题」这类误导；音标同理按空值隐藏；</li>
 * <li><b>词形归一化与相近词</b>：查不到原词时按词尾规则回退到原形（accelerated → accelerate），
 * 结果下方用附加卡片呈现「变形 / 相关单词」与「拼写相近」，点击即可回查，
 * 并通过顶部「← 返回」回到上一次查询的词。</li>
 * </ol>
 */
public class WordSearchLocalFragment extends Fragment {

    /** 例句 / 真题例句折叠前展示的条数 */
    private static final int PREVIEW_COUNT = 5;
    /** 「拼写相近」卡片最多展示的候选数 */
    private static final int SIMILAR_ROW_LIMIT = 8;
    /** 「变形 / 相关单词」卡片最多展示的条目数 */
    private static final int RELATED_ROW_LIMIT = 15;

    private ScrollView scrollResult;
    private TextView tvBack, tvWord, tvPos, tvPhoneticUS, tvPhoneticUK, tvPhoneticSep, tvSearchNote, tvSource;
    private View phoneticRow;
    private LinearLayout extraCardsContainer;

    private Section sectionMeaning, sectionEngDef, sectionExample, sectionRealExam, sectionSynonym;

    /** 查询历史：点击附加卡片里的词条时压栈，支持逐级返回 */
    private final Deque<String> history = new ArrayDeque<>();
    /** 用户手动指定的来源词书（计划词书未收录时可用），null 表示自动择优 */
    private String pinnedBookId;
    private boolean examplesExpanded;
    private boolean realExamExpanded;
    private WordEntry currentEntry;

    private String currentWord;
    private boolean isViewCreated = false;
    private String pendingSearchWord = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_word_local_result, container, false);

        scrollResult = view.findViewById(R.id.scroll_result);
        tvBack = view.findViewById(R.id.tv_back);
        tvWord = view.findViewById(R.id.tv_word);
        tvPos = view.findViewById(R.id.tv_pos);
        phoneticRow = view.findViewById(R.id.phonetic_row);
        tvPhoneticUS = view.findViewById(R.id.tv_phonetic_US);
        tvPhoneticUK = view.findViewById(R.id.tv_phonetic_UK);
        tvPhoneticSep = view.findViewById(R.id.tv_phonetic_separator);
        tvSearchNote = view.findViewById(R.id.tv_search_note);
        tvSource = view.findViewById(R.id.tv_source);
        extraCardsContainer = view.findViewById(R.id.extra_cards_container);

        sectionMeaning = new Section(view.findViewById(R.id.section_meaning), "释义");
        sectionEngDef = new Section(view.findViewById(R.id.section_eng_def), "英文释义");
        sectionExample = new Section(view.findViewById(R.id.section_example), "例句");
        sectionRealExam = new Section(view.findViewById(R.id.section_real_exam), "真题例句");
        sectionSynonym = new Section(view.findViewById(R.id.section_synonym), "同近义词");

        resetResult();
        isViewCreated = true;

        // 如果有待处理的搜索请求，则执行搜索
        if (pendingSearchWord != null) {
            String word = pendingSearchWord;
            pendingSearchWord = null;
            searchWordLocally(word);
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isViewCreated = false;
    }

    /**
     * 顶部搜索框发起的查询：作为一次全新的查询，清空历史与手动指定的来源词书。
     */
    public void searchWordLocally(String word) {
        // 如果视图还没有创建，则保存搜索词，稍后在onCreateView中执行搜索
        if (!isViewCreated) {
            pendingSearchWord = word;
            return;
        }
        if (word == null) {
            return;
        }
        String query = word.trim();
        if (query.isEmpty()) {
            return;
        }
        history.clear();
        pinnedBookId = null;
        renderQuery(query, false);
    }

    /** 点击「变形 / 相关单词」「拼写相近」里的词条：记住当前词，支持返回 */
    private void searchFromLink(String word) {
        if (word == null || word.trim().isEmpty()) {
            return;
        }
        if (currentWord != null && !currentWord.isEmpty()) {
            history.push(currentWord);
        }
        pinnedBookId = null;
        renderQuery(word.trim(), false);
    }

    /** 返回上一次查询的词 */
    private void goBack() {
        if (history.isEmpty()) {
            return;
        }
        pinnedBookId = null;
        renderQuery(history.pop(), true);
    }

    private void renderQuery(@NonNull String query, boolean keepExpanded) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        currentWord = query;
        if (!keepExpanded) {
            examplesExpanded = false;
            realExamExpanded = false;
        }
        resetResult();
        renderResult(context, query);
        renderBackRow();
        if (scrollResult != null) {
            scrollResult.scrollTo(0, 0);
        }
    }

    // ==================== 查询 ====================

    private void renderResult(@NonNull Context context, @NonNull String query) {
        String lower = query.toLowerCase(Locale.ROOT);
        LexiconResourceMap.attachContext(context);
        String preferredBookId = LexiconResourceMap.getPreferredLexiconId(context);

        // 1. 精确命中：优先计划词书（或用户手动指定的词书）
        WordEntry entry = pinnedBookId != null
                ? LexiconResourceMap.findWordInBook(lower, pinnedBookId)
                : LexiconResourceMap.findWordForDisplay(lower, preferredBookId);

        // 2. 未命中则按词形规则回退到原形
        String matchedForm = null;
        if (entry == null) {
            for (String candidate : WordFormNormalizer.baseCandidates(lower)) {
                WordEntry hit = pinnedBookId != null
                        ? LexiconResourceMap.findWordInBook(candidate, pinnedBookId)
                        : LexiconResourceMap.findWordForDisplay(candidate, preferredBookId);
                if (hit != null) {
                    entry = hit;
                    matchedForm = candidate;
                    break;
                }
            }
        }
        if (entry == null && pinnedBookId != null) {
            // 手动指定的词书里找不到，退回自动择优，避免出现空白结果
            pinnedBookId = null;
            entry = LexiconResourceMap.findWordForDisplay(lower, preferredBookId);
        }

        currentEntry = entry;
        if (entry == null) {
            tvWord.setText(query);
            showNote("未在本地词书中找到该单词");
        } else {
            renderEntry(context, entry, matchedForm, preferredBookId);
        }

        // 3. 结果下方的附加卡片：变形/相关单词 + 拼写相近
        Set<String> alreadyShown = new LinkedHashSet<>();
        if (entry != null) {
            alreadyShown.add(entry.getHeadWordLower());
        }
        renderRelatedCard(context, entry, alreadyShown);
        renderSimilarCard(context, matchedForm != null ? matchedForm : lower, preferredBookId, alreadyShown);
    }

    private void renderEntry(@NonNull Context context, @NonNull WordEntry entry, @Nullable String matchedForm,
            @NonNull String preferredBookId) {
        tvWord.setText(entry.getHeadWord());

        String pos = entry.getPos();
        tvPos.setText(pos == null || pos.isEmpty() ? "" : pos + ".");
        tvPos.setVisibility(pos == null || pos.isEmpty() ? View.GONE : View.VISIBLE);

        if (matchedForm != null) {
            showNote("已按原形「" + matchedForm + "」显示");
        }

        renderPhonetics(entry);
        renderMeaning(entry);
        renderEnglishDefinition(entry);
        renderExamples(context, entry);
        renderRealExamSentences(context, entry);
        renderSynonyms(entry);
        renderSource(context, entry, preferredBookId);
    }

    /** 顶部返回入口：仅从附加卡片跳转进来时显示 */
    private void renderBackRow() {
        if (tvBack == null) {
            return;
        }
        if (history.isEmpty()) {
            tvBack.setText("");
            tvBack.setVisibility(View.GONE);
            tvBack.setOnClickListener(null);
            return;
        }
        tvBack.setText("← 返回「" + history.peek() + "」");
        tvBack.setOnClickListener(v -> goBack());
        tvBack.setVisibility(View.VISIBLE);
    }

    /** 音标：空值整体隐藏，避免出现「美音:」这样的空标签（全库约 1.4% 单词无音标） */
    private void renderPhonetics(@NonNull WordEntry entry) {
        String us = entry.getUsPhone() == null ? "" : entry.getUsPhone().trim();
        String uk = entry.getUkPhone() == null ? "" : entry.getUkPhone().trim();
        boolean hasUs = !us.isEmpty();
        boolean hasUk = !uk.isEmpty();

        tvPhoneticUS.setText(hasUs ? "美音 /" + us + "/" : "");
        tvPhoneticUS.setVisibility(hasUs ? View.VISIBLE : View.GONE);
        tvPhoneticUS.setOnClickListener(hasUs
                ? v -> AudioPlayer.playAudio(v.getContext(), entry.getHeadWord(), true)
                : null);

        tvPhoneticUK.setText(hasUk ? "英音 /" + uk + "/" : "");
        tvPhoneticUK.setVisibility(hasUk ? View.VISIBLE : View.GONE);
        tvPhoneticUK.setOnClickListener(hasUk
                ? v -> AudioPlayer.playAudio(v.getContext(), entry.getHeadWord(), false)
                : null);

        boolean both = hasUs && hasUk;
        tvPhoneticSep.setText(both ? "   " : "");
        tvPhoneticSep.setVisibility(both ? View.VISIBLE : View.GONE);

        phoneticRow.setVisibility((hasUs || hasUk) ? View.VISIBLE : View.GONE);
    }

    private void renderMeaning(@NonNull WordEntry entry) {
        String meaning = entry.getChineseTranslation();
        if (meaning == null || meaning.isEmpty()) {
            return;
        }
        addTextBlock(sectionMeaning.container, meaning, R.color.theme_text_primary, 16f);
        sectionMeaning.setVisible(true);
    }

    private void renderEnglishDefinition(@NonNull WordEntry entry) {
        String definition = entry.getEnglishDefinition();
        if (definition == null || definition.isEmpty()) {
            return;
        }
        addTextBlock(sectionEngDef.container, definition, R.color.theme_text_secondary, 14f);
        sectionEngDef.setVisible(true);
    }

    /**
     * 例句：默认前 {@value #PREVIEW_COUNT} 条，其余折叠为「展开全部 N 条」。
     * 折起/展开只重绘本板块，不重新查询。
     */
    private void renderExamples(@NonNull Context context, @NonNull WordEntry entry) {
        List<WordEntry.ExampleSentence> examples = entry.getExampleSentences();
        sectionExample.container.removeAllViews();
        if (examples.isEmpty()) {
            sectionExample.setVisible(false);
            return;
        }
        int shown = examplesExpanded ? examples.size() : Math.min(PREVIEW_COUNT, examples.size());
        LayoutInflater inflater = LayoutInflater.from(context);
        for (int i = 0; i < shown; i++) {
            WordEntry.ExampleSentence sentence = examples.get(i);
            View row = inflater.inflate(R.layout.item_sentence_line, sectionExample.container, false);
            TextView tvEn = row.findViewById(R.id.tv_sentence_en);
            TextView tvCn = row.findViewById(R.id.tv_sentence_cn);
            tvEn.setText(sentence.getEn());
            String cn = sentence.getCn();
            if (cn == null || cn.trim().isEmpty()) {
                tvCn.setVisibility(View.GONE);
            } else {
                tvCn.setText(cn);
                tvCn.setTextColor(ContextCompat.getColor(context, R.color.theme_text_secondary));
            }
            sectionExample.container.addView(row);
        }
        if (examples.size() > PREVIEW_COUNT) {
            addExpandToggle(sectionExample.container, examplesExpanded, examples.size(), () -> {
                examplesExpanded = !examplesExpanded;
                if (currentEntry != null && getContext() != null) {
                    renderExamples(getContext(), currentEntry);
                }
            });
        }
        sectionExample.setVisible(true);
    }

    private void renderRealExamSentences(@NonNull Context context, @NonNull WordEntry entry) {
        List<WordEntry.RealExamSentence> sentences = entry.getRealExamSentences();
        sectionRealExam.container.removeAllViews();
        if (sentences.isEmpty()) {
            sectionRealExam.setVisible(false);
            return;
        }
        int shown = realExamExpanded ? sentences.size() : Math.min(PREVIEW_COUNT, sentences.size());
        LayoutInflater inflater = LayoutInflater.from(context);
        for (int i = 0; i < shown; i++) {
            WordEntry.RealExamSentence sentence = sentences.get(i);
            View row = inflater.inflate(R.layout.item_real_exam_line, sectionRealExam.container, false);
            TextView tvContent = row.findViewById(R.id.tv_real_content);
            TextView tvSourceLine = row.findViewById(R.id.tv_real_source);
            tvContent.setText(sentence.getContent());
            String source = sentence.getSourceLabel();
            if (source == null || source.isEmpty()) {
                tvSourceLine.setVisibility(View.GONE);
            } else {
                tvSourceLine.setText(source);
            }
            sectionRealExam.container.addView(row);
        }
        if (sentences.size() > PREVIEW_COUNT) {
            addExpandToggle(sectionRealExam.container, realExamExpanded, sentences.size(), () -> {
                realExamExpanded = !realExamExpanded;
                if (currentEntry != null && getContext() != null) {
                    renderRealExamSentences(getContext(), currentEntry);
                }
            });
        }
        sectionRealExam.setVisible(true);
    }

    private void renderSynonyms(@NonNull WordEntry entry) {
        List<WordEntry.Synonym> synonyms = entry.getSynonyms();
        if (synonyms.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (WordEntry.Synonym synonym : synonyms) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            String pos = synonym.getPos() == null ? "" : synonym.getPos().trim();
            if (!pos.isEmpty()) {
                sb.append(pos).append(". ");
            }
            sb.append(synonym.getTran());
            List<String> words = synonym.getHwds();
            if (words != null && !words.isEmpty()) {
                sb.append(" —— ");
                for (int i = 0; i < words.size(); i++) {
                    if (i > 0) {
                        sb.append("、");
                    }
                    sb.append(words.get(i));
                }
            }
        }
        addTextBlock(sectionSynonym.container, sb.toString(), R.color.theme_text_primary, 14f);
        sectionSynonym.setVisible(true);
    }

    /**
     * 来源词书：计划词书未收录时明确告知，并提供「切换词书」——
     * 列出收录该词的全部词书（含各书信息量对比），由用户决定展示哪一本。
     */
    private void renderSource(@NonNull Context context, @NonNull WordEntry entry, @NonNull String preferredBookId) {
        String title = LexiconResourceMap.getBookTitle(entry.getBookId());
        if (title.isEmpty()) {
            tvSource.setVisibility(View.GONE);
            return;
        }
        boolean fromPlanBook = !preferredBookId.isEmpty() && preferredBookId.equals(entry.getBookId());
        List<WordEntry> copies = fromPlanBook
                ? new ArrayList<>()
                : LexiconResourceMap.findWordInBooks(entry.getHeadWordLower(), preferredBookId);
        boolean canSwitch = !fromPlanBook && copies.size() > 1;

        String text = fromPlanBook ? "来源：计划词书 · " + title : "来源：" + title;
        if (!preferredBookId.isEmpty() && !fromPlanBook) {
            text += " · 当前计划词书未收录";
        }
        if (canSwitch) {
            text += "　切换词书 ▾";
        }
        tvSource.setText(text);
        tvSource.setTextColor(ContextCompat.getColor(context,
                canSwitch ? R.color.theme_primary : R.color.middle_gray));
        tvSource.setClickable(canSwitch);
        tvSource.setOnClickListener(canSwitch ? v -> showBookPicker(context, copies, preferredBookId) : null);
        tvSource.setVisibility(View.VISIBLE);
    }

    private void showBookPicker(@NonNull Context context, @NonNull List<WordEntry> copies,
            @NonNull String preferredBookId) {
        List<String> bookIds = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        bookIds.add(null);
        labels.add("自动选择（内容最丰富）");
        for (WordEntry entry : copies) {
            String bookId = entry.getBookId();
            String title = LexiconResourceMap.getBookTitle(bookId);
            StringBuilder label = new StringBuilder(title.isEmpty() ? bookId : title);
            if (bookId.equals(preferredBookId)) {
                label.append("（计划词书）");
            }
            if (bookId.equals(pinnedBookId)) {
                label.append("　✓");
            }
            bookIds.add(bookId);
            labels.add(label.toString());
        }
        int checked = bookIds.indexOf(pinnedBookId);
        if (checked < 0) {
            checked = 0;
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle("选择词书")
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (dialog, which) -> {
                    pinnedBookId = bookIds.get(which);
                    dialog.dismiss();
                    renderQuery(currentWord, true);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== 附加卡片 ====================

    /** 「变形 / 相关单词」：来自词书的同根词（含词形变化），点击可回查 */
    private void renderRelatedCard(@NonNull Context context, @Nullable WordEntry entry, @NonNull Set<String> shown) {
        if (entry == null) {
            return;
        }
        List<ExtraRow> rows = new ArrayList<>();
        for (WordEntry.RelatedWord related : entry.getRelatedWords()) {
            for (WordEntry.RelWordItem item : related.getWords()) {
                String word = item.getHwd();
                if (word == null || word.trim().isEmpty() || !shown.add(word.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                String pos = related.getPos() == null ? "" : related.getPos().trim();
                String meaning = item.getTran() == null ? "" : item.getTran();
                rows.add(new ExtraRow(word, pos.isEmpty() ? meaning : pos + ". " + meaning, searchableWord(word)));
                if (rows.size() >= RELATED_ROW_LIMIT) {
                    break;
                }
            }
            if (rows.size() >= RELATED_ROW_LIMIT) {
                break;
            }
        }
        renderExtraCard(context, "变形 / 相关单词", rows);
    }

    /** 「拼写相近」：前缀/回退匹配到的其他词条，点击可回查 */
    private void renderSimilarCard(@NonNull Context context, @NonNull String base, @NonNull String preferredBookId,
            @NonNull Set<String> shown) {
        List<WordEntry> similar = LexiconResourceMap.findSimilarWords(base, preferredBookId, SIMILAR_ROW_LIMIT);
        List<ExtraRow> rows = new ArrayList<>();
        for (WordEntry candidate : similar) {
            if (candidate == null || !shown.add(candidate.getHeadWordLower())) {
                continue;
            }
            rows.add(new ExtraRow(candidate.getHeadWord(), candidate.getChineseTranslation(),
                    candidate.getHeadWord()));
        }
        renderExtraCard(context, "拼写相近", rows);
    }

    private void renderExtraCard(@NonNull Context context, @NonNull String title, @NonNull List<ExtraRow> rows) {
        if (rows.isEmpty() || extraCardsContainer == null) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(context);
        View card = inflater.inflate(R.layout.item_word_extra_card, extraCardsContainer, false);
        ((TextView) card.findViewById(R.id.extra_card_title)).setText(title);
        LinearLayout container = card.findViewById(R.id.extra_card_container);
        for (ExtraRow row : rows) {
            View line = inflater.inflate(R.layout.item_word_extra_line, container, false);
            ((TextView) line.findViewById(R.id.tv_extra_word)).setText(row.word);
            ((TextView) line.findViewById(R.id.tv_extra_meaning)).setText(row.meaning);
            line.setOnClickListener(v -> searchFromLink(row.query));
            container.addView(line);
        }
        extraCardsContainer.addView(card);
    }

    // ==================== 视图工具 ====================

    private void showNote(@NonNull String text) {
        tvSearchNote.setText(text);
        tvSearchNote.setVisibility(View.VISIBLE);
    }

    /**
     * 相关词里可能存在 "long for" 这类词组，词书表只收录单词原形，
     * 直接拿整条去查必然落空，因此取首个单词作为回查词（展示仍保留完整词组）。
     */
    @NonNull
    private static String searchableWord(@NonNull String related) {
        String trimmed = related.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    /** 「展开全部 N 条 / 收起」切换项 */
    private void addExpandToggle(@NonNull LinearLayout container, boolean expanded, int total, @NonNull Runnable action) {
        TextView toggle = new TextView(container.getContext());
        toggle.setText(expanded ? "收起" : "展开全部 " + total + " 条");
        toggle.setTextSize(13f);
        toggle.setTextColor(ContextCompat.getColor(container.getContext(), R.color.theme_stress));
        toggle.setPadding(0, dp(6), 0, dp(2));
        toggle.setOnClickListener(v -> action.run());
        container.addView(toggle);
    }

    private void addTextBlock(@NonNull LinearLayout container, @NonNull String text, int colorRes, float sizeSp) {
        TextView tv = new TextView(container.getContext());
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(ContextCompat.getColor(container.getContext(), colorRes));
        tv.setLineSpacing(dp(2), 1f);
        container.addView(tv);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * 清空上一次查询的所有状态 —— 修复历史上「未找到时残留上一个单词音标与发音回调」的问题。
     */
    private void resetResult() {
        if (tvWord == null) {
            return;
        }
        currentEntry = null;
        tvWord.setText("");
        tvPos.setText("");
        tvPos.setVisibility(View.GONE);
        tvSearchNote.setText("");
        tvSearchNote.setVisibility(View.GONE);
        tvSource.setText("");
        tvSource.setVisibility(View.GONE);
        tvSource.setClickable(false);
        tvSource.setOnClickListener(null);

        tvPhoneticUS.setText("");
        tvPhoneticUS.setVisibility(View.GONE);
        tvPhoneticUS.setOnClickListener(null);
        tvPhoneticUK.setText("");
        tvPhoneticUK.setVisibility(View.GONE);
        tvPhoneticUK.setOnClickListener(null);
        tvPhoneticSep.setText("");
        tvPhoneticSep.setVisibility(View.GONE);
        phoneticRow.setVisibility(View.GONE);

        for (Section section : new Section[] { sectionMeaning, sectionEngDef, sectionExample, sectionRealExam,
                sectionSynonym }) {
            if (section != null) {
                section.container.removeAllViews();
                section.setVisible(false);
            }
        }
        if (extraCardsContainer != null) {
            extraCardsContainer.removeAllViews();
        }
    }

    public Fragment getFragment() {
        return this;
    }

    /** 一个可整体显隐的板块（分隔线 + 小标题 + 内容容器） */
    private static final class Section {
        private final View root;
        private final TextView title;
        private final LinearLayout container;

        Section(@NonNull View root, @NonNull CharSequence titleText) {
            this.root = root;
            this.title = root.findViewById(R.id.section_title);
            this.container = root.findViewById(R.id.section_container);
            this.title.setText(titleText);
        }

        void setVisible(boolean visible) {
            root.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /** 附加卡片中的一行：展示词形 + 简述，点击以 query 回查 */
    private static final class ExtraRow {
        private final String word;
        private final String meaning;
        private final String query;

        ExtraRow(String word, String meaning, String query) {
            this.word = word;
            this.meaning = meaning != null ? meaning : "";
            this.query = query;
        }
    }
}
