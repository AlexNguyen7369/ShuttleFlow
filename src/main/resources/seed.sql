INSERT INTO users (email, password_hash, full_name, role) VALUES
-- BCrypt hashes for the documented local-only test passwords.
('court.manager@shuttleflow.com', '$2a$10$cLUH.RnpLXYLEKUD88ZgROj7nCcTAorHLMAPckntZH1LKazW7fusi', 'Court Manager', 'PROVIDER'),
('coach.kim@shuttleflow.com',     '$2a$10$.gQObBoo78jf2kTIM/4J3eVMh9U9CnyUog27duZ.I51uOUOixybO2', 'Coach Kim',     'PROVIDER'),
('alex@shuttleflow.com',          '$2a$10$YpmeOR7gkPQzLu0kyP5IPeCz4.S/gc/GVtMeTONXWShK15TKLyJye', 'Alex Nguyen',   'CUSTOMER'),
('jamie@shuttleflow.com',         '$2a$10$2tjL5OyS8g/93gxTR4CEFOvcem92OIUbFFoWGqDSOfA6QudRPrRPG', 'Jamie Lee',     'CUSTOMER');

INSERT INTO providers (user_id, name, type, location) VALUES
(1, 'Court 3',   'COURT', 'ShuttleFlow Arena - Building A'),
(2, 'Coach Kim', 'COACH', 'ShuttleFlow Arena - Training Room 1');

INSERT INTO services (provider_id, name, duration_min, max_players, price) VALUES
(1, 'Singles Court Rental',      60, 2, 20.00),
(1, 'Doubles Court Rental',      60, 4, 20.00),
(2, 'Private Coaching Session',  60, 1, 40.00);

-- Slots are relative to the boot date so the demo data is always in the future.
-- Standard SQL interval arithmetic keeps this portable across PostgreSQL and H2.
INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, status) VALUES
(1, 1, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '1' DAY + INTERVAL '9' HOUR,  CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '1' DAY + INTERVAL '10' HOUR, 'OPEN'),
(1, 2, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '1' DAY + INTERVAL '18' HOUR, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '1' DAY + INTERVAL '19' HOUR, 'OPEN'),
(1, 2, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '2' DAY + INTERVAL '10' HOUR, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '2' DAY + INTERVAL '11' HOUR, 'OPEN'),
(1, 1, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '3' DAY + INTERVAL '19' HOUR, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '3' DAY + INTERVAL '20' HOUR, 'OPEN'),
(2, 3, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '1' DAY + INTERVAL '16' HOUR, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '1' DAY + INTERVAL '17' HOUR, 'OPEN'),
(2, 3, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '3' DAY + INTERVAL '15' HOUR, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '3' DAY + INTERVAL '16' HOUR, 'OPEN'),
(2, 3, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '5' DAY + INTERVAL '17' HOUR, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '5' DAY + INTERVAL '18' HOUR, 'OPEN');
