package com.deepsleep.memory.ui.treasure_view.pronunciation_view;

public class WordPhraseItem {
    private String word;
    private String meaning;
    private boolean isCorrectlyPronounced;

    // 构造函数、getter和setter方法
    public WordPhraseItem(String word, String meaning, boolean isCorrectlyPronounced) {
        this.word = word;
        this.meaning = meaning;
        this.isCorrectlyPronounced = isCorrectlyPronounced;
    }
    public String getWord() {
        return word;
    }
    public void setWord(String word) {
        this.word = word;
    }
    public String getMeaning() {
        return meaning;
    }
    public void setMeaning(String meaning) {
        this.meaning = meaning;
    }
    public boolean isCorrectlyPronounced() {
        return isCorrectlyPronounced;
    }
    public void setCorrectlyPronounced(boolean correctlyPronounced) {
        isCorrectlyPronounced = correctlyPronounced;
    }
}
