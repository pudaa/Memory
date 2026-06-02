package com.deepsleep.memory.ui.treasure_view.composition_view;

public class CompositionRecord {
    private String compositionId;
    private String compositionContent;
    private String correctionResult;
    private String createdTime;

    public CompositionRecord(String compositionId, String compositionContent, String correctionResult, String createdTime) {
        this.compositionId = compositionId;
        this.compositionContent = compositionContent;
        this.correctionResult = correctionResult;
        this.createdTime = createdTime;
    }

    public String getCompositionId() {
        return compositionId;
    }

    public void setCompositionId(String compositionId) {
        this.compositionId = compositionId;
    }

    public String getCompositionContent() {
        return compositionContent;
    }

    public void setCompositionContent(String compositionContent) {
        this.compositionContent = compositionContent;
    }

    public String getCorrectionResult() {
        return correctionResult;
    }

    public void setCorrectionResult(String correctionResult) {
        this.correctionResult = correctionResult;
    }

    public String getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(String createdTime) {
        this.createdTime = createdTime;
    }

    // 获取作文内容的前几行作为预览
    public String getPreviewContent() {
        if (compositionContent == null || compositionContent.isEmpty()) {
            return "";
        }

        String[] lines = compositionContent.split("\n");
        StringBuilder preview = new StringBuilder();
        int lineCount = Math.min(3, lines.length);

        for (int i = 0; i < lineCount; i++) {
            if (preview.length() > 0) {
                preview.append("\n");
            }
            preview.append(lines[i]);
        }

        return preview.toString();
    }

    // 从批改结果中提取分数
    public String getScore() {
        if (correctionResult == null || correctionResult.isEmpty()) {
            return "";
        }

        try {
            org.json.JSONObject jsonObject = new org.json.JSONObject(correctionResult);
            org.json.JSONObject scoreObj = jsonObject.getJSONObject("评分");
            return scoreObj.getString("分数");
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            return "";
        }
    }
}
