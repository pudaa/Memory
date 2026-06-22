package com.deepsleep.memory.handle_utils.lexicon.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 词书元数据实体
 */
@Entity(tableName = "lexicon_book")
public class LexiconBookEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "book_id")
    private String bookId;

    @NonNull
    @ColumnInfo(name = "title")
    private String title;

    @ColumnInfo(name = "word_count")
    private int wordCount;

    @ColumnInfo(name = "cover_url")
    private String coverUrl;

    @ColumnInfo(name = "description")
    private String description;

    @ColumnInfo(name = "tags")
    private String tags; // JSON array string

    @ColumnInfo(name = "is_builtin")
    private boolean isBuiltin = true;

    // ====== Constructors ======

    public LexiconBookEntity() {
    }

    @Ignore
    public LexiconBookEntity(@NonNull String bookId, @NonNull String title, int wordCount, String coverUrl,
            String description, String tags, boolean isBuiltin) {
        this.bookId = bookId;
        this.title = title;
        this.wordCount = wordCount;
        this.coverUrl = coverUrl;
        this.description = description;
        this.tags = tags;
        this.isBuiltin = isBuiltin;
    }

    // ====== Getters & Setters ======

    @NonNull
    public String getBookId() {
        return bookId;
    }

    public void setBookId(@NonNull String bookId) {
        this.bookId = bookId;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    public void setTitle(@NonNull String title) {
        this.title = title;
    }

    public int getWordCount() {
        return wordCount;
    }

    public void setWordCount(int wordCount) {
        this.wordCount = wordCount;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public boolean isBuiltin() {
        return isBuiltin;
    }

    public void setBuiltin(boolean builtin) {
        isBuiltin = builtin;
    }
}
