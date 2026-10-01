package com.deepsleep.memory.handle_utils.lexicon;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 词形归一化 —— 把查询词还原成词书里可能收录的原形候选。
 *
 * <p>
 * 词书只收录原形（head word），而用户/文本里出现的是变形（accelerated / running / studies）。
 * 这里不做词干化库依赖，只用「词尾规则 + 常见不规则表」产出一组<b>候选原形</b>，
 * 由调用方逐个回查本地词书验证——能查到才用，因此不会把 bus 误判成 bu。
 * </p>
 *
 * <p>
 * 返回顺序即优先级：先尝试最有把握的规则（复数/三单 → 过去式 → 进行式 → 比较级 → 副词），
 * 调用方命中第一个即停。
 * </p>
 */
public final class WordFormNormalizer {

    private WordFormNormalizer() {
    }

    /** 常见不规则变形 → 原形（覆盖高频不规则动词/名词/形容词） */
    private static final Map<String, String> IRREGULAR = new HashMap<>();

    static {
        put("went", "go", "gone", "go", "goes", "go", "going", "go");
        put("was", "be", "were", "be", "been", "be", "is", "be", "are", "be", "am", "be");
        put("did", "do", "does", "do", "done", "do", "doing", "do");
        put("had", "have", "has", "have", "having", "have");
        put("made", "make", "making", "make", "said", "say", "saying", "say");
        put("took", "take", "taken", "take", "taking", "take");
        put("got", "get", "gotten", "get", "getting", "get");
        put("ran", "run", "running", "run", "came", "come", "coming", "come");
        put("saw", "see", "seen", "see", "seeing", "see");
        put("knew", "know", "known", "know", "thought", "think");
        put("bought", "buy", "brought", "bring", "found", "find", "felt", "feel");
        put("left", "leave", "kept", "keep", "meant", "mean", "met", "meet");
        put("paid", "pay", "sent", "send", "sat", "sit", "slept", "sleep");
        put("spoke", "speak", "spoken", "speak", "spent", "spend", "stood", "stand");
        put("taught", "teach", "told", "tell", "understood", "understand");
        put("wrote", "write", "written", "write", "writing", "write");
        put("children", "child", "men", "man", "women", "woman");
        put("teeth", "tooth", "feet", "foot", "mice", "mouse", "geese", "goose");
        put("better", "good", "best", "good", "worse", "bad", "worst", "bad");
        put("further", "far", "furthest", "far", "farther", "far", "farthest", "far");
        put("studies", "study", "studied", "study", "studying", "study");
        put("lives", "life", "lived", "live", "living", "live");
    }

    private static void put(String... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            IRREGULAR.put(pairs[i], pairs[i + 1]);
        }
    }

    /**
     * 产出该查询词可能的原形候选（已去掉输入本身、去重、保持优先级顺序）。
     *
     * @param word 已小写、已 trim 的查询词
     */
    @NonNull
    public static List<String> baseCandidates(@NonNull String word) {
        String w = word.trim().toLowerCase(Locale.ROOT);
        if (w.isEmpty() || !w.matches(".*[a-z].*")) {
            return Collections.emptyList();
        }
        Set<String> out = new LinkedHashSet<>();

        String irregular = IRREGULAR.get(w);
        if (irregular != null) {
            out.add(irregular);
        }

        // 复数 / 三单
        if (w.length() > 4 && w.endsWith("ies")) {
            out.add(w.substring(0, w.length() - 3) + "y");
        }
        if (w.length() > 3 && (w.endsWith("es") || w.endsWith("ches") || w.endsWith("shes"))) {
            out.add(w.substring(0, w.length() - 2));
        }
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is")) {
            out.add(w.substring(0, w.length() - 1));
        }

        // 过去式 / 过去分词
        if (w.length() > 4 && w.endsWith("ied")) {
            out.add(w.substring(0, w.length() - 3) + "y");
        }
        if (w.length() > 3 && w.endsWith("ed")) {
            String stem = w.substring(0, w.length() - 2);
            if (isDoubledConsonant(stem)) {
                out.add(stem.substring(0, stem.length() - 1));
            }
            out.add(stem);
            out.add(stem + "e");
        }

        // 进行式 / 动名词
        if (w.length() > 4 && w.endsWith("ing")) {
            String stem = w.substring(0, w.length() - 3);
            if (isDoubledConsonant(stem)) {
                out.add(stem.substring(0, stem.length() - 1));
            }
            out.add(stem);
            out.add(stem + "e");
        }

        // 比较级 / 最高级
        if (w.length() > 5 && w.endsWith("est")) {
            String stem = w.substring(0, w.length() - 3);
            if (isDoubledConsonant(stem)) {
                out.add(stem.substring(0, stem.length() - 1));
            }
            out.add(stem);
            out.add(stem + "e");
        }
        if (w.length() > 4 && w.endsWith("er")) {
            String stem = w.substring(0, w.length() - 2);
            if (isDoubledConsonant(stem)) {
                out.add(stem.substring(0, stem.length() - 1));
            }
            out.add(stem);
            out.add(stem + "e");
        }

        // 副词
        if (w.length() > 4 && w.endsWith("ly")) {
            String stem = w.substring(0, w.length() - 2);
            out.add(stem);
            out.add(stem + "le");
        }

        out.remove(w);
        out.remove("");
        List<String> result = new ArrayList<>();
        for (String c : out) {
            if (c.length() >= 2) {
                result.add(c);
            }
        }
        return result;
    }

    /** 词尾双写辅音（running → runn / stopped → stopp） */
    private static boolean isDoubledConsonant(@NonNull String stem) {
        if (stem.length() < 2) {
            return false;
        }
        char last = stem.charAt(stem.length() - 1);
        char prev = stem.charAt(stem.length() - 2);
        return last == prev && "bdgklmnprstz".indexOf(last) >= 0;
    }
}
