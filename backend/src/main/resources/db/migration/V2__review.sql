-- BookyGeek V2: book reviews and ratings (AMD-002, owner-approved; data model section 2.12, gate G-DM).
-- One review per customer per book; no edit and no delete, so there is no updated_at.

CREATE TABLE review (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    book_id     BIGINT       NOT NULL REFERENCES book (id),
    customer_id BIGINT       NOT NULL REFERENCES customer (id) ON DELETE CASCADE,
    rating      INTEGER      NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     VARCHAR(100) NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_review_book_customer UNIQUE (book_id, customer_id)
);
CREATE INDEX idx_review_book ON review (book_id);
