PRAGMA journal_mode=WAL;
PRAGMA synchronous=NORMAL;
PRAGMA temp_store=MEMORY;
PRAGMA cache_size=-200000;

-- 训练样本（你导入的历史样本）
CREATE TABLE IF NOT EXISTS train_samples (
     id INTEGER PRIMARY KEY AUTOINCREMENT,
     title TEXT NOT NULL,
     author TEXT NOT NULL,
     category TEXT NOT NULL,
     tags_json TEXT NOT NULL,        -- JSON array string
     title_norm TEXT NOT NULL,
     author_norm TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_train_title_norm ON train_samples(title_norm);
CREATE INDEX IF NOT EXISTS idx_train_author_norm ON train_samples(author_norm);
CREATE INDEX IF NOT EXISTS idx_train_title_author_norm ON train_samples(title_norm, author_norm);
CREATE INDEX IF NOT EXISTS idx_train_category ON train_samples(category);

-- category 与 tags 一一映射（闭集真相来源）
CREATE TABLE IF NOT EXISTS category_tag_map (
    category TEXT PRIMARY KEY,
    tags_json TEXT NOT NULL         -- JSON array string
);
