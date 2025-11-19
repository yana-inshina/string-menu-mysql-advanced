CREATE DATABASE IF NOT EXISTS string_menu DEFAULT CHARACTER SET utf8mb4;
USE string_menu;

CREATE TABLE IF NOT EXISTS string_ops_adv (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  op_type      VARCHAR(40) NOT NULL,      -- 'SUBSTRING' | 'CASE' | 'SEARCH_ENDS'
  source_text  TEXT,

  -- SUBSTRING
  from_idx     INT NULL,
  to_idx       INT NULL,
  substring    TEXT NULL,

  -- CASE
  upper_text   TEXT NULL,
  lower_text   TEXT NULL,

  -- SEARCH_ENDS
  query_text   TEXT NULL,
  found_pos    INT NULL,                  -- -1 если не найдено
  ends_with    BOOLEAN NULL,

  note         VARCHAR(255) NULL
);
