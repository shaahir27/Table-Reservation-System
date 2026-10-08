-- ============================================================================
-- RTRS v2 - Migration C: Floor-plan geometry (optional; UI falls back to a card grid when NULL)
-- ============================================================================
ALTER TABLE restaurant_table ADD (
    pos_x  NUMBER(4),                                  -- grid units (1 unit = 24 px in the editor)
    pos_y  NUMBER(4),
    shape  VARCHAR2(5) DEFAULT 'RECT' NOT NULL
);
ALTER TABLE restaurant_table ADD CONSTRAINT ck_table_shape CHECK (shape IN ('RECT','ROUND'));
ALTER TABLE restaurant_table ADD CONSTRAINT ck_table_pos   CHECK ((pos_x IS NULL AND pos_y IS NULL) OR (pos_x >= 0 AND pos_y >= 0));
