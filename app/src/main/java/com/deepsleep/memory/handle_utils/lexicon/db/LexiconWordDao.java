package com.deepsleep.memory.handle_utils.lexicon.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.deepsleep.memory.handle_utils.lexicon.WordEntry;

import java.util.List;

/**
 * 单词条目 DAO —— 直接返回 {@link WordEntry}
 */
@Dao
public interface LexiconWordDao {

    @Query("SELECT * FROM word_entry WHERE book_id = :bookId ORDER BY word_rank")
    List<WordEntry> getWordsByBookId(String bookId);

    @Query("SELECT * FROM word_entry WHERE book_id = :bookId AND word_rank = :wordRank LIMIT 1")
    WordEntry getWordByBookIdAndRank(String bookId, int wordRank);

    @Query("SELECT * FROM word_entry WHERE head_word_lower = :headWordLower LIMIT 1")
    WordEntry searchByHeadWord(String headWordLower);

    /** 指定词书内精确查词（本地查词优先命中「当前学习计划词书」） */
    @Query("SELECT * FROM word_entry WHERE head_word_lower = :headWordLower AND book_id = :bookId LIMIT 1")
    WordEntry searchInBook(String headWordLower, String bookId);

    /** 全词书精确查词（同一单词可能存在于多本词书，内容详略不同，由调用方择优） */
    @Query("SELECT * FROM word_entry WHERE head_word_lower = :headWordLower")
    List<WordEntry> searchInAllBooks(String headWordLower);

    /** 收录该单词的全部词书条目（计划词书排前，供查词页「切换词书」使用） */
    @Query("SELECT * FROM word_entry WHERE head_word_lower = :headWordLower"
            + " ORDER BY (book_id = :preferredBookId) DESC, book_id")
    List<WordEntry> searchInBooksOrdered(String headWordLower, String preferredBookId);

    /**
     * 前缀匹配（拼写相近 / 词形变化候选）。
     *
     * <p>
     * 使用 GLOB 而非 LIKE：列已统一小写且 GLOB 区分大小写，
     * 因此 `head_word_lower GLOB 'abc*'` 能走 index_word_entry_head_word_lower 索引；
     * LIKE 默认大小写不敏感，无法用该索引，会退化成 15 万行全表扫描。
     * </p>
     *
     * <p>
     * 排序：计划词书优先 → 词长更短（更接近原形）→ 字典序；取回后再由调用方按 head_word 去重。
     * </p>
     */
    @Query("SELECT * FROM word_entry WHERE head_word_lower GLOB :pattern AND head_word_lower != :exclude"
            + " ORDER BY (book_id = :preferredBookId) DESC, length(head_word_lower), head_word_lower LIMIT :limit")
    List<WordEntry> searchByPrefix(String pattern, String exclude, String preferredBookId, int limit);

    @Query("SELECT COUNT(*) FROM word_entry WHERE book_id = :bookId")
    int getWordCountByBookId(String bookId);

    @Query("SELECT head_word FROM word_entry WHERE book_id = :bookId ORDER BY RANDOM() LIMIT :limit")
    List<String> getRandomHeadWords(String bookId, int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertWord(WordEntry word);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertWords(List<WordEntry> words);
}
