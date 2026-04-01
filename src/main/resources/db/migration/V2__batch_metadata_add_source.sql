-- batch_metadata 식별자를 (source, file_name) 복합 키로 변경
-- 기존 행은 모두 opinet 배치 수집 결과이므로 source = 'opinet'으로 초기화
ALTER TABLE batch_metadata ADD COLUMN source VARCHAR(50) NOT NULL DEFAULT 'opinet';
ALTER TABLE batch_metadata DROP PRIMARY KEY;
ALTER TABLE batch_metadata ADD PRIMARY KEY (source, file_name);
