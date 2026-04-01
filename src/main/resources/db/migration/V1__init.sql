CREATE TABLE gas_station (
    id          VARCHAR(20)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    brand       VARCHAR(10)  NOT NULL,
    address     VARCHAR(200),
    is_self     BOOLEAN      NOT NULL DEFAULT FALSE,
    type        VARCHAR(20)  NOT NULL DEFAULT 'GAS_STATION',
    latitude    DOUBLE       NOT NULL,
    longitude   DOUBLE       NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE gas_station_price (
    station_id  VARCHAR(20) NOT NULL,
    fuel_type   VARCHAR(20) NOT NULL,
    price       INT         NOT NULL,
    updated_at  DATE        NOT NULL,
    PRIMARY KEY (station_id, fuel_type),
    FOREIGN KEY (station_id) REFERENCES gas_station (id)
);

CREATE TABLE batch_metadata (
    file_name   VARCHAR(100) NOT NULL,
    last_hash   VARCHAR(64)  NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    PRIMARY KEY (file_name)
);
