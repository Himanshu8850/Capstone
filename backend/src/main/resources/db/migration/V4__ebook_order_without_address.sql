-- BookyGeek V4: an order that holds only e-books has nothing to deliver, so it carries no address (AMD-004).
-- The address snapshot columns become optional; the format checks still apply whenever a value is present.

ALTER TABLE orders
    ALTER COLUMN ship_full_name DROP NOT NULL,
    ALTER COLUMN ship_phone     DROP NOT NULL,
    ALTER COLUMN ship_line1     DROP NOT NULL,
    ALTER COLUMN ship_city      DROP NOT NULL,
    ALTER COLUMN ship_state     DROP NOT NULL,
    ALTER COLUMN ship_pin_code  DROP NOT NULL,
    ALTER COLUMN ship_country   DROP NOT NULL;
