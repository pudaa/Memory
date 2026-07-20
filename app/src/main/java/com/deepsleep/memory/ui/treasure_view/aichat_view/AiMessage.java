package com.deepsleep.memory.ui.treasure_view.aichat_view;

public class AiMessage {
    public static final int TYPE_USER = 0;
    public static final int TYPE_ASSISTANT = 1;

    private int type;
    private String content;
    private String audioUrl;
    private int score = -1; // -1 表示无评分

    // 🆕 v2.0 新增字段
    private String feedback; // AI 反馈文案
    private String asrTranscript; // 语音识别文本（用户语音消息时）
    private long messageId; // 消息 ID
    private boolean audioPending; // 音频是否在后台生成中
    private String level; // 等级（excellent/good/fair/poor）
    private double pronunciationScore = -1; // 发音分
    private double fluencyScore = -1; // 流利度
    private double grammarScore = -1; // 语法分
    private double vocabularyScore = -1; // 词汇分

    // 语音消息：用户本地录音文件路径
    private String localAudioPath;
    private boolean isVoiceMessage;

    private AiMessage(int type, String content, String audioUrl, int score) {
        this.type = type;
        this.content = content;
        this.audioUrl = audioUrl;
        this.score = score;
    }

    public static AiMessage user(String content) {
        return new AiMessage(TYPE_USER, content, null, -1);
    }

    /** 创建用户语音消息（含本地录音路径） */
    public static AiMessage userVoice(String localAudioPath) {
        AiMessage msg = new AiMessage(TYPE_USER, null, null, -1);
        msg.localAudioPath = localAudioPath;
        msg.isVoiceMessage = true;
        return msg;
    }

    public static AiMessage assistant(String content, String audioUrl, int score) {
        return new AiMessage(TYPE_ASSISTANT, content, audioUrl, score);
    }

    // ========== Getters & Setters ==========

    public String getLocalAudioPath() {
        return localAudioPath;
    }

    public void setLocalAudioPath(String p) {
        this.localAudioPath = p;
    }

    public boolean isVoiceMessage() {
        return isVoiceMessage;
    }

    public int getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public boolean hasAudio() {
        return audioUrl != null && !audioUrl.isEmpty() && !"null".equals(audioUrl);
    }

    public boolean isAudioPending() {
        return audioPending;
    }

    public void setAudioPending(boolean audioPending) {
        this.audioPending = audioPending;
    }

    public boolean hasScore() {
        return score >= 0;
    }

    // 🆕 v2.0 Getters & Setters

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public String getAsrTranscript() {
        return asrTranscript;
    }

    public void setAsrTranscript(String asrTranscript) {
        this.asrTranscript = asrTranscript;
    }

    public long getMessageId() {
        return messageId;
    }

    public void setMessageId(long messageId) {
        this.messageId = messageId;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public double getPronunciationScore() {
        return pronunciationScore;
    }

    public void setPronunciationScore(double pronunciationScore) {
        this.pronunciationScore = pronunciationScore;
    }

    public double getFluencyScore() {
        return fluencyScore;
    }

    public void setFluencyScore(double fluencyScore) {
        this.fluencyScore = fluencyScore;
    }

    public double getGrammarScore() {
        return grammarScore;
    }

    public void setGrammarScore(double grammarScore) {
        this.grammarScore = grammarScore;
    }

    public double getVocabularyScore() {
        return vocabularyScore;
    }

    public void setVocabularyScore(double vocabularyScore) {
        this.vocabularyScore = vocabularyScore;
    }

    public boolean hasEvaluation() {
        return pronunciationScore >= 0 || fluencyScore >= 0 || grammarScore >= 0 || vocabularyScore >= 0;
    }

    public boolean isCorrect() {
        return score >= 85;
    }

    // ==================== 对话总结相关 ====================

    public static final int TYPE_SUMMARY = 2;

    private boolean isSummary = false;
    private int summaryWordsUsed = 0;
    private int summaryCorrections = 0;
    private int summaryTurnCount = 0;

    public boolean isSummary() { return isSummary; }
    public void setSummary(boolean summary) { isSummary = summary; }
    public int getSummaryWordsUsed() { return summaryWordsUsed; }
    public void setSummaryWordsUsed(int wordsUsed) { summaryWordsUsed = wordsUsed; }
    public int getSummaryCorrections() { return summaryCorrections; }
    public void setSummaryCorrections(int corrections) { summaryCorrections = corrections; }
    public int getSummaryTurnCount() { return summaryTurnCount; }
    public void setSummaryTurnCount(int turnCount) { summaryTurnCount = turnCount; }
}
