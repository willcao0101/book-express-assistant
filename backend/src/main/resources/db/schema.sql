PRAGMA journal_mode=WAL;
PRAGMA synchronous=NORMAL;
PRAGMA temp_store=MEMORY;
PRAGMA cache_size=-200000;

-- train sample
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

-- category and tags mapping
CREATE TABLE IF NOT EXISTS category_tag_map (
    category TEXT PRIMARY KEY,
    tags_json TEXT NOT NULL         -- JSON array string
);


CREATE TABLE IF NOT EXISTS shopify_account (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email TEXT NOT NULL,
    shop_domain TEXT NOT NULL,
    client_id TEXT NOT NULL,
    client_secret_enc TEXT NOT NULL,
    access_token_enc TEXT,
    is_default INTEGER DEFAULT 0,
    created_at TEXT,
    updated_at TEXT
);

CREATE TABLE IF NOT EXISTS sync_record (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER,
    product_id TEXT,
    status TEXT,
    message TEXT,
    created_at TEXT
);

CREATE TABLE IF NOT EXISTS validation_rule (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    field_path TEXT NOT NULL,
    rule_type TEXT NOT NULL,
    rule_config TEXT,
    severity TEXT DEFAULT 'ERROR',
    message TEXT NOT NULL,
    enabled INTEGER DEFAULT 1,
    created_at TEXT,
    updated_at TEXT
);

CREATE TABLE IF NOT EXISTS sync_change_item (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sync_record_id INTEGER NOT NULL,
    field_path TEXT NOT NULL,
    old_value TEXT,
    new_value TEXT,
    status TEXT,
    message TEXT,
    created_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_validation_rule_enabled ON validation_rule(enabled);
CREATE INDEX IF NOT EXISTS idx_change_item_record ON sync_change_item(sync_record_id);
