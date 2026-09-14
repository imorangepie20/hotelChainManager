ALTER TABLE reservation
    ADD COLUMN IF NOT EXISTS guest_phone VARCHAR(30);
