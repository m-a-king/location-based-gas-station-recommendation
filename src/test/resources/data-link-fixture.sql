-- 테스트 fixture: 주유소(WGS84 128.0, 38.0) 주변 moct_link 데이터
-- KATEC (400000, 600000) → WGS84 (128.0, 38.0) (KATEC 투영 원점)

DELETE FROM moct_link;

-- 주유소 앞 도로 (동행): 주유소에서 ~11m 북쪽, 동쪽 방향
INSERT INTO moct_link (link_id, f_node, t_node, road_name, road_rank, road_no, lanes, max_spd, length, f_longitude, f_latitude, t_longitude, t_latitude)
VALUES ('3280033641', 'N001', 'N002', '금오대로', '103', '3280', 4, 60, 334.0, 127.9985, 38.0001, 128.0015, 38.0001);

-- 주유소 앞 도로 (서행): 위 도로의 역방향
INSERT INTO moct_link (link_id, f_node, t_node, road_name, road_rank, road_no, lanes, max_spd, length, f_longitude, f_latitude, t_longitude, t_latitude)
VALUES ('3280033642', 'N002', 'N001', '금오대로', '103', '3280', 4, 60, 334.0, 128.0015, 38.0001, 127.9985, 38.0001);

-- 고속도로 (동행): 주유소에서 ~167m 북쪽
INSERT INTO moct_link (link_id, f_node, t_node, road_name, road_rank, road_no, lanes, max_spd, length, f_longitude, f_latitude, t_longitude, t_latitude)
VALUES ('1050012345', 'N003', 'N004', '경부고속도로', '101', '1050', 4, 100, 800.0, 127.9990, 38.0015, 128.0030, 38.0016);

-- 이면도로 (북행): 주유소에서 동쪽으로 약간 떨어진 남북 도로
INSERT INTO moct_link (link_id, f_node, t_node, road_name, road_rank, road_no, lanes, max_spd, length, f_longitude, f_latitude, t_longitude, t_latitude)
VALUES ('3280033650', 'N005', 'N006', '구미로', '106', '3281', 2, 40, 250.0, 128.0010, 37.9980, 128.0010, 38.0005);

-- bounding box 밖의 도로 (매칭되면 안 됨)
INSERT INTO moct_link (link_id, f_node, t_node, road_name, road_rank, road_no, lanes, max_spd, length, f_longitude, f_latitude, t_longitude, t_latitude)
VALUES ('9999999999', 'N099', 'N100', '먼도로', '103', '9999', 2, 60, 500.0, 128.0100, 38.0100, 128.0110, 38.0110);
