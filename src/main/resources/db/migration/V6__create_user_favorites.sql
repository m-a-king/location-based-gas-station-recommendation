CREATE TABLE user_favorites (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    gas_station_id  VARCHAR(255) NOT NULL,
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_favorites_user_station (user_id, gas_station_id),
    CONSTRAINT fk_user_favorites_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_favorites_user_created ON user_favorites (user_id, created_at);
