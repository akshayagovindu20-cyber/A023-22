CREATE DATABASE IF NOT EXISTS distributed_event_booking
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE distributed_event_booking;

CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS events (
    event_id BIGINT NOT NULL AUTO_INCREMENT,
    event_name VARCHAR(160) NOT NULL,
    event_date DATETIME NOT NULL,
    venue VARCHAR(200) NOT NULL,
    total_seats INT NOT NULL,
    available_seats INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id),
    CONSTRAINT chk_events_total_seats CHECK (total_seats > 0),
    CONSTRAINT chk_events_available_seats CHECK (
        available_seats >= 0 AND available_seats <= total_seats
    ),
    KEY idx_events_date (event_date)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS bookings (
    booking_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    seats_booked INT NOT NULL,
    booking_status ENUM('CONFIRMED', 'CANCELLED') NOT NULL DEFAULT 'CONFIRMED',
    booking_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (booking_id),
    CONSTRAINT chk_bookings_seats CHECK (seats_booked > 0),
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_event FOREIGN KEY (event_id)
        REFERENCES events (event_id) ON DELETE RESTRICT,
    KEY idx_bookings_user_time (user_id, booking_time),
    KEY idx_bookings_event_status (event_id, booking_status)
) ENGINE=InnoDB;

INSERT INTO events (event_name, event_date, venue, total_seats, available_seats)
SELECT 'Technology Innovation Summit',
       '2027-03-15 11:00:00', 'Main Auditorium', 120, 120
WHERE NOT EXISTS (
    SELECT 1 FROM events WHERE event_name = 'Technology Innovation Summit'
);

INSERT INTO events (event_name, event_date, venue, total_seats, available_seats)
SELECT 'Campus Music Night',
       '2027-04-02 18:00:00', 'Open Air Theatre', 80, 80
WHERE NOT EXISTS (
    SELECT 1 FROM events WHERE event_name = 'Campus Music Night'
);

INSERT INTO events (event_name, event_date, venue, total_seats, available_seats)
SELECT 'Entrepreneurship Workshop',
       '2027-04-18 09:30:00', 'Seminar Hall B', 45, 45
WHERE NOT EXISTS (
    SELECT 1 FROM events WHERE event_name = 'Entrepreneurship Workshop'
);
