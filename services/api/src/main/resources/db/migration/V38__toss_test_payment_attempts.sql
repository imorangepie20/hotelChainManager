CREATE TABLE payment_provider_attempt (
  id UUID PRIMARY KEY,
  reservation_id UUID NOT NULL REFERENCES reservation(id),
  provider VARCHAR(30) NOT NULL,
  merchant_account VARCHAR(80) NOT NULL,
  order_id VARCHAR(64) NOT NULL,
  payment_key VARCHAR(160),
  idempotency_key VARCHAR(100) NOT NULL,
  amount_krw BIGINT NOT NULL CHECK (amount_krw > 0),
  currency CHAR(3) NOT NULL CHECK (currency = 'KRW'),
  status VARCHAR(20) NOT NULL CHECK (status IN ('NEW','APPROVING','SUCCEEDED','FAILED','UNKNOWN')),
  provider_event_id VARCHAR(160),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(provider, order_id),
  UNIQUE(provider, idempotency_key)
);
