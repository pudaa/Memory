package com.deepsleep.memory.ui.main_view;

public class WordCard {
    public int word_id;
    public String word;
    public String phonetic;
    public String usPhone;
    public String ukPhone;
    public String definition;
    public String example;
    public int listId;
    public int type;
    public int day;
    public int originalDay;
    public int positionInList;
    public boolean isNewList;
    public boolean isMastered;
    public boolean isFavorite;
    public boolean isOperated;

    // ========== FSRS 字段 ==========
    /** 可提取性 (0-1)，越接近0越急需复习 */
    public double retrievability;
    /** 单词难度 (1-10)，越高越难 */
    public double difficulty;
    /** 记忆稳定性（天），越大记忆越稳定 */
    public double stability;
    /** 上次评分 (0-4)，0=未评, 1=Again, 2=Hard, 3=Good, 4=Easy */
    public int lastScore;

    // ========== 练习相关字段 ==========
    /** 卡片展示时间戳，用于计算 responseTimeMs */
    public long displayStartTime;
    /** 用户是否回答正确（客户端判断） */
    public boolean isCorrect;

    // ========== 输入模式字段 ==========
    /** 用户输入的释义（输入模式） */
    public String userAnswer;
    /** 参考释义（标准答案，输入模式） */
    public String referenceDefinition;
    /** 词性（如 "v", "n"，输入模式） */
    public String pos;
    /** AI 评分（1-4，输入模式，从 API 响应更新） */
    public int fsrsScore;
    /** AI 反馈文本（从 API 响应更新） */
    public String aiFeedback;

    public static final int TYPE_NEW = 0;
    public static final int TYPE_REVIEW = 1;

    // ========== 学习模式常量 ==========
    public static final String MODE_CHOICE = "choice";
    public static final String MODE_INPUT = "input";

    public WordCard(int word_id, String word, String phonetic, String definition, String example) {
        this.word_id = word_id;
        this.word = word;
        this.phonetic = phonetic;
        this.definition = definition;
        this.example = example;
    }

    // 设置美式和英式发音的网址
    public void setPhone(String usPhone, String ukPhone) {
        this.usPhone = usPhone;
        this.ukPhone = ukPhone;
    }

    /** 重置练习状态（每次展示卡片前调用） */
    public void resetExerciseState() {
        this.displayStartTime = System.currentTimeMillis();
        this.isCorrect = false;
    }
}
