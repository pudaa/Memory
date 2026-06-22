#!/usr/bin/env python3
"""
词书 JSON → SQLite 数据库转换脚本（全字段版 v2）

用法:
  python convert_lexicon_json_to_sqlite.py

输入:
  - app/src/main/res/raw/book_list.json
  - app/src/main/res/raw/*.json

输出:
  - app/src/main/assets/databases/lexicon.db

表结构: lexicon_book + word_entry（涵盖 JSON 中所有字段）
"""

import json
import os
import sqlite3
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
RAW_DIR = os.path.join(PROJECT_ROOT, "app", "src", "main", "res", "raw")
OUTPUT_DIR = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets", "databases")
BOOK_LIST_FILE = os.path.join(RAW_DIR, "book_list.json")
OUTPUT_DB = os.path.join(OUTPUT_DIR, "lexicon.db")


def log(msg):
    print(f"[lexicon2db] {msg}")


def load_book_list():
    with open(BOOK_LIST_FILE, "r", encoding="utf-8") as f:
        data = json.load(f)
    books = []
    for book in data["data"]["normalBooksInfo"]:
        books.append({
            "book_id": book["id"],
            "title": book.get("title", ""),
            "word_count": book.get("wordNum", 0),
            "cover_url": book.get("cover", ""),
            "description": book.get("introduce", ""),
            "tags": json.dumps(book.get("tags", []), ensure_ascii=False),
            "is_builtin": True,
        })
    return books


def extract_word_entry(book_id, word_obj):
    """
    从单个单词 JSON 对象中提取所有字段，返回用于插入 word_entry 表的元组。
    """
    head_word = word_obj.get("headWord", "")
    word_rank = word_obj.get("wordRank", 0)

    content = word_obj.get("content", {})
    word = content.get("word", {})
    inner = word.get("content", {})

    word_id = word.get("wordId", "")
    us_phone = inner.get("usphone", "")
    uk_phone = inner.get("ukphone", "")
    phone = inner.get("phone", "")
    us_speech = inner.get("usspeech", "")
    uk_speech = inner.get("ukspeech", "")
    speech = inner.get("speech", "")
    star = inner.get("star", 0)

    # ---- 翻译 ----
    trans_list = inner.get("trans", [])
    cn_items = []
    en_items = []
    pos = ""
    for t in trans_list:
        if "tranCn" in t:
            cn_items.append({"tranCn": t["tranCn"], "pos": t.get("pos", ""), "descCn": t.get("descCn", "")})
        if "tranOther" in t:
            en_items.append({"tranOther": t["tranOther"], "pos": t.get("pos", "")})
        if not pos and "pos" in t:
            pos = t["pos"]
    cn_json = json.dumps(cn_items, ensure_ascii=False, separators=(',', ':'))
    en_json = json.dumps(en_items, ensure_ascii=False, separators=(',', ':'))

    # ---- 例句 ----
    sentence = inner.get("sentence", {})
    ex_sentences = sentence.get("sentences", [])
    ex_items = []
    for s in ex_sentences:
        ex_items.append({
            "sContent": s.get("sContent", ""),
            "sCn": s.get("sCn", ""),
            "sContent_eng": s.get("sContent_eng", ""),
            "sSpeech": s.get("sSpeech", ""),
        })
    ex_json = json.dumps(ex_items, ensure_ascii=False, separators=(',', ':'))

    # ---- 真题例句 ----
    real_exam = inner.get("realExamSentence", {})
    re_sentences = real_exam.get("sentences", [])
    re_items = []
    for r in re_sentences:
        si = r.get("sourceInfo", {}) or {}
        re_items.append({
            "sContent": r.get("sContent", ""),
            "paper": si.get("paper", ""),
            "level": si.get("level", ""),
            "year": si.get("year", ""),
            "type": si.get("type", ""),
        })
    re_json = json.dumps(re_items, ensure_ascii=False, separators=(',', ':'))

    # ---- 同近义词 ----
    syno = inner.get("syno", {})
    synos = syno.get("synos", [])
    syn_items = []
    for s in synos:
        hwds = [{"w": h.get("w", "")} for h in s.get("hwds", [])]
        syn_items.append({
            "pos": s.get("pos", ""),
            "tran": s.get("tran", ""),
            "hwds": hwds,
        })
    syn_json = json.dumps(syn_items, ensure_ascii=False, separators=(',', ':'))

    # ---- 同根词 ----
    rel_word = inner.get("relWord", {})
    rels = rel_word.get("rels", [])
    rel_items = []
    for r in rels:
        words = [{"hwd": w.get("hwd", ""), "tran": w.get("tran", "")} for w in r.get("words", [])]
        rel_items.append({
            "pos": r.get("pos", ""),
            "words": words,
        })
    rel_json = json.dumps(rel_items, ensure_ascii=False, separators=(',', ':'))

    return (
        book_id,
        word_rank,
        head_word,
        head_word.lower(),
        word_id,
        us_phone,
        uk_phone,
        phone,
        us_speech,
        uk_speech,
        speech,
        star,
        pos,
        cn_json,
        en_json,
        ex_json,
        re_json,
        syn_json,
        rel_json,
    )


def create_tables(conn):
    conn.executescript("""
        PRAGMA user_version = 2;

        CREATE TABLE IF NOT EXISTS lexicon_book (
            book_id     TEXT PRIMARY KEY NOT NULL,
            title       TEXT NOT NULL,
            word_count  INTEGER NOT NULL,
            cover_url   TEXT,
            description TEXT,
            tags        TEXT,
            is_builtin  INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS word_entry (
            id                       INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            book_id                  TEXT NOT NULL,
            word_rank                INTEGER NOT NULL,
            head_word                TEXT NOT NULL,
            head_word_lower          TEXT NOT NULL,
            word_id                  TEXT NOT NULL,
            us_phone                 TEXT NOT NULL,
            uk_phone                 TEXT NOT NULL,
            phone                    TEXT NOT NULL,
            us_speech                TEXT NOT NULL,
            uk_speech                TEXT NOT NULL,
            speech                   TEXT NOT NULL,
            star                     INTEGER NOT NULL,
            pos                      TEXT NOT NULL,
            chinese_translations_json  TEXT NOT NULL,
            english_definitions_json   TEXT NOT NULL,
            example_sentences_json     TEXT NOT NULL,
            real_exam_sentences_json   TEXT NOT NULL,
            synonyms_json              TEXT NOT NULL,
            related_words_json         TEXT NOT NULL,
            FOREIGN KEY (book_id) REFERENCES lexicon_book(book_id) ON DELETE CASCADE
        );

        CREATE INDEX IF NOT EXISTS index_word_entry_book_id
            ON word_entry(book_id);
        CREATE UNIQUE INDEX IF NOT EXISTS index_word_entry_book_id_word_rank
            ON word_entry(book_id, word_rank);
        CREATE INDEX IF NOT EXISTS index_word_entry_head_word_lower
            ON word_entry(head_word_lower);
    """)


def main():
    if not os.path.isfile(BOOK_LIST_FILE):
        log(f"错误: 找不到 book_list.json —— {BOOK_LIST_FILE}")
        sys.exit(1)

    os.makedirs(OUTPUT_DIR, exist_ok=True)

    if os.path.exists(OUTPUT_DB):
        os.remove(OUTPUT_DB)
        log(f"已删除旧数据库: {OUTPUT_DB}")

    conn = sqlite3.connect(OUTPUT_DB)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    conn.execute("PRAGMA cache_size=-64000")  # 64MB cache for faster inserts

    create_tables(conn)

    # ---- 导入书本元数据 ----
    books = load_book_list()
    log(f"从 book_list.json 读取到 {len(books)} 本词书")

    for book in books:
        conn.execute(
            """INSERT OR REPLACE INTO lexicon_book
               (book_id, title, word_count, cover_url, description, tags, is_builtin)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            (book["book_id"], book["title"], book["word_count"],
             book["cover_url"], book["description"], book["tags"],
             1 if book["is_builtin"] else 0)
        )

    # ---- 导入单词数据 ----
    total_words = 0
    skipped_books = []

    INSERT_SQL = """INSERT OR REPLACE INTO word_entry
        (book_id, word_rank, head_word, head_word_lower, word_id,
         us_phone, uk_phone, phone, us_speech, uk_speech, speech,
         star, pos, chinese_translations_json, english_definitions_json,
         example_sentences_json, real_exam_sentences_json,
         synonyms_json, related_words_json)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""

    for book in books:
        book_id = book["book_id"]
        json_path = os.path.join(RAW_DIR, f"{book_id}.json")

        if not os.path.isfile(json_path):
            skipped_books.append(book_id)
            log(f"  跳过: {book_id} (JSON 不存在)")
            continue

        try:
            with open(json_path, "r", encoding="utf-8") as f:
                words = json.load(f)
        except (json.JSONDecodeError, FileNotFoundError) as e:
            skipped_books.append(book_id)
            log(f"  解析失败: {book_id} —— {e}")
            continue

        batch = [extract_word_entry(book_id, w) for w in words]
        conn.executemany(INSERT_SQL, batch)

        log(f"  已导入: {book_id} ({len(batch)} 词)  —— {book['title']}")
        total_words += len(batch)

    conn.commit()

    # ---- 统计 ----
    book_count = conn.execute("SELECT COUNT(*) FROM lexicon_book").fetchone()[0]
    word_count = conn.execute("SELECT COUNT(*) FROM word_entry").fetchone()[0]
    db_size = os.path.getsize(OUTPUT_DB) / 1024 / 1024

    # 验证字段完整性：随机抽查一条
    sample = conn.execute(
        "SELECT head_word, word_id, chinese_translations_json, example_sentences_json, "
        "real_exam_sentences_json, synonyms_json, related_words_json "
        "FROM word_entry WHERE book_id = 'cet4_1' AND word_rank = 1"
    ).fetchone()

    log(f"\n{'='*50}")
    log(f"导入完成！")
    log(f"  词书数量: {book_count}")
    log(f"  单词总量: {word_count}")
    if skipped_books:
        log(f"  跳过的词书: {', '.join(skipped_books)}")
    log(f"  输出文件: {OUTPUT_DB}")
    log(f"  文件大小: {db_size:.2f} MB")
    log(f"{'='*50}")

    if sample:
        log(f"  字段验证 (cet4_1 #1):")
        log(f"    head_word = {sample[0]}")
        log(f"    word_id   = {sample[1]}")
        log(f"    cn_trans  = {sample[2][:80]}...")
        log(f"    examples  = {sample[3][:80]}...")
        log(f"    real_exam = {sample[4][:80]}...")
        log(f"    synonyms  = {sample[5][:80]}...")
        log(f"    rel_words = {sample[6][:80]}...")

    log(f"\n数据库已就绪，请用 Android Studio 编译项目。")
    conn.close()


if __name__ == "__main__":
    main()



if __name__ == "__main__":
    main()
