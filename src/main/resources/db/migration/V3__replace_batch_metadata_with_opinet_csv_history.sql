CREATE TABLE opinet_csv_history (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    file_name  VARCHAR(100) NOT NULL,
    last_hash  VARCHAR(64)  NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_opinet_csv_history_file_name (file_name)
);

INSERT INTO opinet_csv_history (file_name, last_hash, updated_at)
SELECT file_name, last_hash, updated_at FROM batch_metadata WHERE source = 'opinet';

DROP TABLE batch_metadata;
