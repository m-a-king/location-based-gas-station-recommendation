CREATE TABLE users (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    kakao_sub       VARCHAR(64)  NOT NULL,
    name            VARCHAR(100),
    car_model       VARCHAR(100),
    fuel_type       VARCHAR(20),
    fuel_efficiency DOUBLE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_kakao_sub (kakao_sub)
);
