package com.deepsleep.memory.handle_utils.lexicon.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * 词书元数据 DAO
 */
@Dao
public interface LexiconBookDao {

    @Query("SELECT * FROM lexicon_book ORDER BY book_id")
    List<LexiconBookEntity> getAllBooks();

    @Query("SELECT * FROM lexicon_book WHERE book_id = :bookId")
    LexiconBookEntity getBookById(String bookId);

    @Query("SELECT title FROM lexicon_book WHERE book_id = :bookId")
    String getBookTitleById(String bookId);

    @Query("SELECT COUNT(*) FROM lexicon_book")
    int getBookCount();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBook(LexiconBookEntity book);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBooks(List<LexiconBookEntity> books);
}
