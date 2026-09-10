CREATE TABLE schema_marker (
    id SMALLINT PRIMARY KEY,
    description VARCHAR(100) NOT NULL
);

INSERT INTO schema_marker (id, description)
VALUES (1, 'hotel-chain-api baseline');
