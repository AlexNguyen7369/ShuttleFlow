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

INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, status) VALUES
(1, 1, '2026-09-27 09:00:00', '2026-09-27 10:00:00', 'OPEN'),
(1, 2, '2026-09-27 18:00:00', '2026-09-27 19:00:00', 'OPEN'),
(1, 2, '2026-09-28 10:00:00', '2026-09-28 11:00:00', 'OPEN'),
(2, 3, '2026-09-27 16:00:00', '2026-09-27 17:00:00', 'OPEN'),
(2, 3, '2026-09-29 15:00:00', '2026-09-29 16:00:00', 'OPEN');
