-- BookyGeek V3: short writer biographies shown in "About the writer" (AMD-003, owner request).
-- One row per author name as it appears in book.author. Read-only reference data; no edit or delete use case.

CREATE TABLE author_profile (
    name VARCHAR(120) PRIMARY KEY,
    bio  VARCHAR(600) NOT NULL CHECK (btrim(bio) <> '')
);
