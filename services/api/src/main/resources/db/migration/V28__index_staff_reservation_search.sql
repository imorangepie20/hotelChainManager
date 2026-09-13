CREATE INDEX reservation_staff_stay_search_idx
    ON reservation (room_type_id, check_in, check_out);
