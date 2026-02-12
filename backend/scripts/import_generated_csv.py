#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Import generated CSV records into SQLite books table.

Default paths (relative to this script directory):
- CSV: ./generated_taxonomy_records.csv
- DB : ../data/books.db

Important:
- Remove trailing [number] from book_title during import.
  e.g. "Understanding Sticker [10]" -> "Understanding Sticker"

Usage:
  python3 import_generated_csv.py
  python3 import_generated_csv.py --csv ./your_file.csv --db ../data/books.db
  python3 import_generated_csv.py --truncate-taxonomy
"""

import argparse
import csv
import datetime as dt
import json
import re
import sqlite3
import uuid
from pathlib import Path
from typing import Dict, List, Optional


def normalize_text(s: str) -> str:
    if s is None:
        return ""
    s = s.strip().lower()
    s = re.sub(r"[^a-z0-9\s]+", " ", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s


def clean_title(title: str) -> str:
    """
    Remove trailing [number] from title.
    Examples:
      "Foo [12]" -> "Foo"
      "Foo[12]"  -> "Foo"
    """
    if not title:
        return ""
    t = title.strip()
    t = re.sub(r"\s*\[\d+\]\s*$", "", t)
    return t.strip()


def table_info(conn: sqlite3.Connection, table: str):
    cur = conn.execute(f"PRAGMA table_info({table})")
    rows = cur.fetchall()
    if not rows:
        raise RuntimeError(f"Table '{table}' not found.")
    return rows


def columns_set(info) -> set:
    return {r[1] for r in info}


def detect_id_mode(info) -> Optional[str]:
    """
    Returns:
      - INTEGER_AUTOINC
      - TEXT_UUID
      - None
    """
    id_row = next((r for r in info if r[1] == "id"), None)
    if id_row is None:
        return None
    col_type = (id_row[2] or "").upper()
    if "INT" in col_type:
        return "INTEGER_AUTOINC"
    return "TEXT_UUID"


def validate_or_fix_shopify_tags(raw: str) -> str:
    """
    Keep shopify_tags as JSON array string.
    If malformed, try best-effort fix:
      - plain tag -> ["tag"]
    """
    if raw is None:
        return "[]"
    s = raw.strip()
    if not s:
        return "[]"

    try:
        obj = json.loads(s)
        if isinstance(obj, list):
            # normalize list to compact JSON string
            return json.dumps(obj, ensure_ascii=False)
        if isinstance(obj, str):
            return json.dumps([obj], ensure_ascii=False)
        return "[]"
    except Exception:
        # fallback: treat as single tag text
        return json.dumps([s], ensure_ascii=False)


def maybe_truncate(conn: sqlite3.Connection, cols: set):
    if "source_type" in cols:
        conn.execute("DELETE FROM books WHERE source_type = 'taxonomy_seed'")
    else:
        conn.execute("DELETE FROM books WHERE book_title LIKE '__TAXONOMY__::%'")
    conn.commit()


def build_insert_cols(cols: set, id_mode: Optional[str]) -> List[str]:
    required = [
        "book_title", "book_author", "book_title_norm", "book_author_norm",
        "trademe_categories", "shopify_tags"
    ]
    for c in required:
        if c not in cols:
            raise RuntimeError(f"books table missing required column: {c}")

    insert_cols = []
    if "id" in cols and id_mode == "TEXT_UUID":
        insert_cols.append("id")

    insert_cols.extend(required)

    if "created_at" in cols:
        insert_cols.append("created_at")
    if "updated_at" in cols:
        insert_cols.append("updated_at")
    if "source_type" in cols:
        insert_cols.append("source_type")

    return insert_cols


def read_csv_rows(csv_path: Path) -> List[Dict[str, str]]:
    rows = []
    with csv_path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        required_headers = {"book_title", "book_author", "trademe_categories", "shopify_tags"}
        missing = required_headers - set(reader.fieldnames or [])
        if missing:
            raise RuntimeError(f"CSV missing headers: {sorted(missing)}")

        for r in reader:
            rows.append(r)
    return rows


def import_rows(conn: sqlite3.Connection, csv_rows: List[Dict[str, str]], insert_cols: List[str]) -> int:
    placeholders = ",".join(["?"] * len(insert_cols))
    sql = f"INSERT INTO books ({','.join(insert_cols)}) VALUES ({placeholders})"

    now = dt.datetime.utcnow().isoformat(timespec="seconds") + "Z"
    inserted = 0

    for r in csv_rows:
        raw_title = (r.get("book_title") or "").strip()
        clean = clean_title(raw_title)
        author = (r.get("book_author") or "").strip()
        category = (r.get("trademe_categories") or "").strip()
        tags_json = validate_or_fix_shopify_tags(r.get("shopify_tags"))

        data = {
            "id": str(uuid.uuid4()),
            "book_title": clean,
            "book_author": author,
            "book_title_norm": normalize_text(clean),
            "book_author_norm": normalize_text(author),
            "trademe_categories": category,
            "shopify_tags": tags_json,
            "created_at": now,
            "updated_at": now,
            "source_type": "taxonomy_seed",
        }

        params = [data[c] for c in insert_cols]
        conn.execute(sql, params)
        inserted += 1

    conn.commit()
    return inserted


def main():
    script_dir = Path(__file__).resolve().parent

    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--csv",
        default=str((script_dir / "generated_taxonomy_records.csv").resolve()),
        help="CSV path (default: ./generated_taxonomy_records.csv)"
    )
    parser.add_argument(
        "--db",
        default=str((script_dir / "../data/books.db").resolve()),
        help="SQLite DB path (default: ../data/books.db)"
    )
    parser.add_argument(
        "--truncate-taxonomy",
        action="store_true",
        help="Delete old taxonomy seed rows before import"
    )
    args = parser.parse_args()

    csv_path = Path(args.csv).expanduser().resolve()
    db_path = Path(args.db).expanduser().resolve()

    if not csv_path.exists():
        raise FileNotFoundError(f"CSV not found: {csv_path}")
    if not db_path.exists():
        raise FileNotFoundError(f"DB not found: {db_path}")

    csv_rows = read_csv_rows(csv_path)

    conn = sqlite3.connect(str(db_path))
    try:
        info = table_info(conn, "books")
        cols = columns_set(info)
        mode = detect_id_mode(info)
        insert_cols = build_insert_cols(cols, mode)

        if args.truncate_taxonomy:
            maybe_truncate(conn, cols)

        inserted = import_rows(conn, csv_rows, insert_cols)

        print(f"CSV file:       {csv_path}")
        print(f"Database file:  {db_path}")
        print(f"id mode:        {mode}")
        print(f"Rows in CSV:    {len(csv_rows)}")
        print(f"Inserted rows:  {inserted}")
        print("Title cleanup:  removed trailing [number] pattern")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
