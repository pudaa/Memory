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

    @Query("SELECT COUNT(*) FROM word_entry WHERE book_id = :bookId")
    int getWordCountByBookId(String bookId);

    @Query("SELECT head_word FROM word_entry WHERE book_id = :bookId ORDER BY RANDOM() LIMIT :limit")
    List<String> getRandomHeadWords(String bookId, int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertWord(WordEntry word);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertWords(List<WordEntry> words);
}
