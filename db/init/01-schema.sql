CREATE TABLE IF NOT EXISTS moct_link (
    link_id     VARCHAR(20) PRIMARY KEY,
    f_node      VARCHAR(20) NOT NULL,
    t_node      VARCHAR(20) NOT NULL,
    road_name   VARCHAR(100),
    road_rank   VARCHAR(10),
    road_no     VARCHAR(20),
    lanes       INT,
    max_spd     INT,
    length      DOUBLE,
    f_longitude DOUBLE NOT NULL,
    f_latitude  DOUBLE NOT NULL,
    t_longitude DOUBLE NOT NULL,
    t_latitude  DOUBLE NOT NULL
);
