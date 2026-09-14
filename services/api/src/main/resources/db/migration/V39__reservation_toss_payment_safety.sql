CREATE UNIQUE INDEX payment_provider_attempt_active_reservation_idx
    ON payment_provider_attempt (reservation_id, provider)
    WHERE status IN ('NEW', 'APPROVING', 'UNKNOWN');

CREATE UNIQUE INDEX payment_provider_attempt_payment_key_idx
    ON payment_provider_attempt (provider, payment_key)
    WHERE payment_key IS NOT NULL;
