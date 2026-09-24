DROP TABLE IF EXISTS appointments;
DROP TABLE IF EXISTS availability_slots;
DROP TABLE IF EXISTS services;
DROP TABLE IF EXISTS providers;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    user_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    full_name      VARCHAR(255) NOT NULL,
    role           VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'PROVIDER')),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE providers (
    provider_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(user_id),
    name         VARCHAR(255) NOT NULL,
    type         VARCHAR(20)  NOT NULL CHECK (type IN ('COURT', 'COACH')),
    location     VARCHAR(255)
);

CREATE TABLE services (
    service_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id    BIGINT NOT NULL REFERENCES providers(provider_id),
    name           VARCHAR(255) NOT NULL,
    duration_min   INT NOT NULL CHECK (duration_min > 0),
    max_players    INT NOT NULL DEFAULT 1,
    price          DECIMAL(8,2) NOT NULL DEFAULT 0
);

-- Double-booking guard: a court/coach cannot be listed twice for the same start time.
CREATE TABLE availability_slots (
    slot_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id  BIGINT NOT NULL REFERENCES providers(provider_id),
    service_id   BIGINT NOT NULL REFERENCES services(service_id),
    start_time   TIMESTAMP NOT NULL,
    end_time     TIMESTAMP NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'BOOKED', 'CANCELLED')),
    CHECK (end_time > start_time),
    UNIQUE (provider_id, start_time)
);

-- Double-booking guard: UNIQUE(slot_id) means the database itself rejects a
-- second appointment for a slot, even if two requests race to insert at once.
CREATE TABLE appointments (
    appointment_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    slot_id         BIGINT NOT NULL UNIQUE REFERENCES availability_slots(slot_id),
    user_id         BIGINT NOT NULL REFERENCES users(user_id),
    status          VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    booked_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
