-- ============================================================
-- V17: DEMO DATA - Seed Bangalore Central Road Network & Safe Zones
-- ============================================================
-- All records below are clearly labelled DEMO DATA (AGENTS.md §2.5)

-- 1. Insert Safe Zones (Police Stations, Hospitals, 24/7 Pharmacies)
INSERT INTO safe_zones (id, name, zone_type, latitude, longitude, geometry, address, phone_number, operating_hours, is_24h, source, verification_status)
VALUES
(uuid_generate_v4(), 'Cubbon Park Police Station (DEMO)', 'POLICE_STATION', 12.9760, 77.5995, ST_SetSRID(ST_MakePoint(77.5995, 12.9760), 4326), 'Kasturba Road, Bangalore', '080-22942583', '24 Hours', TRUE, 'DEMO DATA', 'OFFICIALLY_VERIFIED'),
(uuid_generate_v4(), 'Ashok Nagar Police Station (DEMO)', 'POLICE_STATION', 12.9716, 77.6074, ST_SetSRID(ST_MakePoint(77.6074, 12.9716), 4326), 'Commisariat Rd, Ashok Nagar, Bangalore', '080-22942584', '24 Hours', TRUE, 'DEMO DATA', 'OFFICIALLY_VERIFIED'),
(uuid_generate_v4(), 'Bowring & Lady Curzon Hospital (DEMO)', 'HOSPITAL', 12.9818, 77.6015, ST_SetSRID(ST_MakePoint(77.6015, 12.9818), 4326), 'Lady Curzon Rd, Tasker Town, Bangalore', '080-25591362', '24 Hours Emergency', TRUE, 'DEMO DATA', 'OFFICIALLY_VERIFIED'),
(uuid_generate_v4(), 'Apollo Pharmacy 24x7 - Brigade Rd (DEMO)', 'PHARMACY', 12.9730, 77.6072, ST_SetSRID(ST_MakePoint(77.6072, 12.9730), 4326), 'Brigade Road, Bangalore', '080-25588888', '24 Hours', TRUE, 'DEMO DATA', 'COMMUNITY_VERIFIED'),
(uuid_generate_v4(), 'MG Road Metro Station (DEMO)', 'PUBLIC_TRANSPORT', 12.9756, 77.6066, ST_SetSRID(ST_MakePoint(77.6066, 12.9756), 4326), 'MG Road, Bangalore', '080-25191091', '05:00 - 23:00', FALSE, 'DEMO DATA', 'OFFICIALLY_VERIFIED');

-- 2. Insert Connected Walkable Road Network in Central Bangalore (MG Road / Brigade Rd / Church St)
-- Node 1: MG Road Metro Station (12.9756, 77.6066)
-- Node 2: MG Rd & Brigade Rd Junction (12.9745, 77.6068)
-- Node 3: Brigade Road Midpoint (12.9730, 77.6071)
-- Node 4: Ashok Nagar / Brigade Rd South (12.9716, 77.6074)
-- Node 5: Church Street West (12.9750, 77.6015)
-- Node 6: Church Street Mid (12.9748, 77.6042)
-- Node 7: Rest House Road Mid (12.9732, 77.6040)

-- Segment 1: MG Road (Node 1 <-> Node 2)
INSERT INTO road_segments (id, osm_way_id, start_node_id, end_node_id, road_name, road_type, is_lit, max_speed, is_oneway, walkable, length_meters, estimated_walk_time_s, start_point, end_point, geometry)
VALUES
(uuid_generate_v4(), 10001, 1, 2, 'Mahatma Gandhi Road', 'primary', TRUE, 40, FALSE, TRUE, 130.0, 93.0,
 ST_SetSRID(ST_MakePoint(77.6066, 12.9756), 4326),
 ST_SetSRID(ST_MakePoint(77.6068, 12.9745), 4326),
 ST_SetSRID(ST_MakeLine(ST_MakePoint(77.6066, 12.9756), ST_MakePoint(77.6068, 12.9745)), 4326));

-- Segment 2: Brigade Road Upper (Node 2 <-> Node 3)
INSERT INTO road_segments (id, osm_way_id, start_node_id, end_node_id, road_name, road_type, is_lit, max_speed, is_oneway, walkable, length_meters, estimated_walk_time_s, start_point, end_point, geometry)
VALUES
(uuid_generate_v4(), 10002, 2, 3, 'Brigade Road Upper', 'secondary', TRUE, 30, FALSE, TRUE, 170.0, 121.0,
 ST_SetSRID(ST_MakePoint(77.6068, 12.9745), 4326),
 ST_SetSRID(ST_MakePoint(77.6071, 12.9730), 4326),
 ST_SetSRID(ST_MakeLine(ST_MakePoint(77.6068, 12.9745), ST_MakePoint(77.6071, 12.9730)), 4326));

-- Segment 3: Brigade Road Lower (Node 3 <-> Node 4)
INSERT INTO road_segments (id, osm_way_id, start_node_id, end_node_id, road_name, road_type, is_lit, max_speed, is_oneway, walkable, length_meters, estimated_walk_time_s, start_point, end_point, geometry)
VALUES
(uuid_generate_v4(), 10003, 3, 4, 'Brigade Road Lower', 'secondary', TRUE, 30, FALSE, TRUE, 160.0, 114.0,
 ST_SetSRID(ST_MakePoint(77.6071, 12.9730), 4326),
 ST_SetSRID(ST_MakePoint(77.6074, 12.9716), 4326),
 ST_SetSRID(ST_MakeLine(ST_MakePoint(77.6071, 12.9730), ST_MakePoint(77.6074, 12.9716)), 4326));

-- Segment 4: Church Street East (Node 2 <-> Node 6)
INSERT INTO road_segments (id, osm_way_id, start_node_id, end_node_id, road_name, road_type, is_lit, max_speed, is_oneway, walkable, length_meters, estimated_walk_time_s, start_point, end_point, geometry)
VALUES
(uuid_generate_v4(), 10004, 2, 6, 'Church Street East', 'pedestrian', TRUE, 20, FALSE, TRUE, 290.0, 207.0,
 ST_SetSRID(ST_MakePoint(77.6068, 12.9745), 4326),
 ST_SetSRID(ST_MakePoint(77.6042, 12.9748), 4326),
 ST_SetSRID(ST_MakeLine(ST_MakePoint(77.6068, 12.9745), ST_MakePoint(77.6042, 12.9748)), 4326));

-- Segment 5: Church Street West (Node 6 <-> Node 5)
INSERT INTO road_segments (id, osm_way_id, start_node_id, end_node_id, road_name, road_type, is_lit, max_speed, is_oneway, walkable, length_meters, estimated_walk_time_s, start_point, end_point, geometry)
VALUES
(uuid_generate_v4(), 10005, 6, 5, 'Church Street West', 'pedestrian', TRUE, 20, FALSE, TRUE, 300.0, 214.0,
 ST_SetSRID(ST_MakePoint(77.6042, 12.9748), 4326),
 ST_SetSRID(ST_MakePoint(77.6015, 12.9750), 4326),
 ST_SetSRID(ST_MakeLine(ST_MakePoint(77.6042, 12.9748), ST_MakePoint(77.6015, 12.9750)), 4326));

-- Segment 6: Rest House Road (Node 6 <-> Node 7) - Dimmer lighting (Alternative higher risk route)
INSERT INTO road_segments (id, osm_way_id, start_node_id, end_node_id, road_name, road_type, is_lit, max_speed, is_oneway, walkable, length_meters, estimated_walk_time_s, start_point, end_point, geometry)
VALUES
(uuid_generate_v4(), 10006, 6, 7, 'Rest House Road', 'residential', FALSE, 30, FALSE, TRUE, 200.0, 143.0,
 ST_SetSRID(ST_MakePoint(77.6042, 12.9748), 4326),
 ST_SetSRID(ST_MakePoint(77.6040, 12.9732), 4326),
 ST_SetSRID(ST_MakeLine(ST_MakePoint(77.6042, 12.9748), ST_MakePoint(77.6040, 12.9732)), 4326));

-- Segment 7: Museum Road Link (Node 7 <-> Node 4)
INSERT INTO road_segments (id, osm_way_id, start_node_id, end_node_id, road_name, road_type, is_lit, max_speed, is_oneway, walkable, length_meters, estimated_walk_time_s, start_point, end_point, geometry)
VALUES
(uuid_generate_v4(), 10007, 7, 4, 'Museum Road Link', 'secondary', TRUE, 30, FALSE, TRUE, 410.0, 293.0,
 ST_SetSRID(ST_MakePoint(77.6040, 12.9732), 4326),
 ST_SetSRID(ST_MakePoint(77.6074, 12.9716), 4326),
 ST_SetSRID(ST_MakeLine(ST_MakePoint(77.6040, 12.9732), ST_MakePoint(77.6074, 12.9716)), 4326));

-- Segment 8: Rest House Crescent (Node 7 <-> Node 3)
INSERT INTO road_segments (id, osm_way_id, start_node_id, end_node_id, road_name, road_type, is_lit, max_speed, is_oneway, walkable, length_meters, estimated_walk_time_s, start_point, end_point, geometry)
VALUES
(uuid_generate_v4(), 10008, 7, 3, 'Rest House Crescent', 'residential', TRUE, 20, FALSE, TRUE, 340.0, 243.0,
 ST_SetSRID(ST_MakePoint(77.6040, 12.9732), 4326),
 ST_SetSRID(ST_MakePoint(77.6071, 12.9730), 4326),
 ST_SetSRID(ST_MakeLine(ST_MakePoint(77.6040, 12.9732), ST_MakePoint(77.6071, 12.9730)), 4326));
