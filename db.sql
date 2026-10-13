-- TranSeatManagementSystem
-- Database: transeatsys_db
-- MySQL/XAMPP database required by the Java JDBC application.

CREATE DATABASE IF NOT EXISTS transeatsys_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE transeatsys_db;

CREATE TABLE admins (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE drivers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    driver_id VARCHAR(30) NOT NULL UNIQUE,
    driver_name VARCHAR(100) NOT NULL UNIQUE,
    plate_number VARCHAR(30) NOT NULL,
    is_archived TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TIMESTAMP NULL
) ENGINE=InnoDB;

-- Durable archive list. Driver records remain in drivers so related records
-- and original driver IDs are preserved; this table powers the archive dashboard.
CREATE TABLE IF NOT EXISTS archivedrivers (
    driver_id VARCHAR(30) NOT NULL PRIMARY KEY,
    driver_name VARCHAR(100) NOT NULL,
    plate_number VARCHAR(30) NOT NULL,
    archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_archivedriver_driver
        FOREIGN KEY (driver_id) REFERENCES drivers(driver_id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

-- Migrate any drivers already marked archived before this table existed.
INSERT INTO archivedrivers (driver_id, driver_name, plate_number, archived_at)
SELECT driver_id, driver_name, plate_number, COALESCE(archived_at, CURRENT_TIMESTAMP)
FROM drivers
WHERE is_archived=1
ON DUPLICATE KEY UPDATE
    driver_name=VALUES(driver_name),
    plate_number=VALUES(plate_number),
    archived_at=VALUES(archived_at);

CREATE TABLE routes (
    route_code VARCHAR(10) PRIMARY KEY,
    route_name VARCHAR(100) NOT NULL,
    origin VARCHAR(50) NOT NULL,
    destination VARCHAR(50) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE route_stops (
    id INT AUTO_INCREMENT PRIMARY KEY,
    route_code VARCHAR(10) NOT NULL,
    stop_order INT NOT NULL,
    stop_name VARCHAR(120) NOT NULL,
    UNIQUE KEY uq_route_stop_order (route_code, stop_order),
    UNIQUE KEY uq_route_stop_name (route_code, stop_name),
    CONSTRAINT fk_route_stop_route
        FOREIGN KEY (route_code) REFERENCES routes(route_code)
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE departures (
    id INT AUTO_INCREMENT PRIMARY KEY,
    driver_pk INT NOT NULL UNIQUE,
    route_code VARCHAR(10) NOT NULL,
    from_location VARCHAR(50) NOT NULL,
    to_location VARCHAR(50) NOT NULL,
    departure_hour TINYINT NOT NULL,
    departure_minute TINYINT NOT NULL DEFAULT 0,
    departure_period ENUM('AM','PM') NOT NULL,
    current_stop VARCHAR(120) NOT NULL,
    current_stop_at DATETIME NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 0,
    round_trips INT NOT NULL DEFAULT 0,
    departed_at DATETIME NULL,
    ended_at DATETIME NULL,
    end_reason ENUM(
        'Completed round trip',
        'Accident',
        'Flat tire',
        'Engine failure',
        'Crime involving passengers',
        'Natural disaster'
    ) NULL,
    round_trip_earnings DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_departure_driver
        FOREIGN KEY (driver_pk) REFERENCES drivers(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_departure_route
        FOREIGN KEY (route_code) REFERENCES routes(route_code)
) ENGINE=InnoDB;

CREATE TABLE departure_stop_times (
    id INT AUTO_INCREMENT PRIMARY KEY,
    departure_pk INT NOT NULL,
    stop_name VARCHAR(120) NOT NULL,
    passed_at DATETIME NOT NULL,
    UNIQUE KEY uq_departure_stop (departure_pk, stop_name),
    CONSTRAINT fk_stop_time_departure
        FOREIGN KEY (departure_pk) REFERENCES departures(id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE earnings (
    id INT AUTO_INCREMENT PRIMARY KEY,
    driver_pk INT NOT NULL UNIQUE,
    total_earnings DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_earnings_driver
        FOREIGN KEY (driver_pk) REFERENCES drivers(id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE earning_transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    driver_pk INT NOT NULL,
    transaction_type ENUM('EARNING','PENALTY') NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transaction_driver
        FOREIGN KEY (driver_pk) REFERENCES drivers(id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE visitors (
    id INT AUTO_INCREMENT PRIMARY KEY,
    visitor_name VARCHAR(100) NOT NULL,
    login_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    logout_at DATETIME NULL,
    INDEX idx_visitors_login (login_at)
) ENGINE=InnoDB;

CREATE TABLE current_earnings (
    id INT AUTO_INCREMENT PRIMARY KEY,
    driver_pk INT NOT NULL,
    departure_pk INT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    earning_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_current_earning_driver
        FOREIGN KEY (driver_pk) REFERENCES drivers(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_current_earning_departure
        FOREIGN KEY (departure_pk) REFERENCES departures(id)
        ON DELETE CASCADE,
    INDEX idx_current_earning_driver_date (driver_pk, earning_date)
) ENGINE=InnoDB;

-- Default administrator used by the Java login.
-- Username: admin
-- Secret password: admin12345678
INSERT INTO admins (username, password_hash)
VALUES (
    'admin',
    '3c8872c094682f4c3fcdffdab80a8d351dbeee9b31a29e6ea092b51cf473e732'
)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash);

-- Permanent TranSeat driver accounts.
INSERT INTO drivers (driver_id, driver_name, plate_number, is_archived) VALUES
    ('01', 'Vince082197', 'KVJP90', 0),
    ('02', 'Doug080808', 'ABC123', 0),
    ('03', 'Ronald212121', 'DEF88C', 0),
    ('04', 'Louie345678', 'XYZFG6', 0),
    ('05', 'Jacob123412', 'JKAUG9', 0)
ON DUPLICATE KEY UPDATE
    driver_name = VALUES(driver_name),
    plate_number = VALUES(plate_number);

-- The only supported route families.
INSERT INTO routes (route_code, route_name, origin, destination)
VALUES
    ('R1', 'Lumbia to Cogon', 'Lumbia', 'Cogon'),
    ('R2', 'Lumbia to Carmen', 'Lumbia', 'Carmen')
ON DUPLICATE KEY UPDATE
    route_name = VALUES(route_name),
    origin = VALUES(origin),
    destination = VALUES(destination);

-- R1: Lumbia <-> Cogon
INSERT INTO route_stops (route_code, stop_order, stop_name) VALUES
    ('R1', 1, 'Centro Lumbia'),
    ('R1', 2, 'Palalan Road'),
    ('R1', 3, 'Masterson Ave.'),
    ('R1', 4, 'Kagay-an Bridge'),
    ('R1', 5, 'Rodelsa Circle'),
    ('R1', 6, 'Tomas Saco St.'),
    ('R1', 7, 'Fernandez St.'),
    ('R1', 8, 'Corrales Ave.'),
    ('R1', 9, 'Hayes St.'),
    ('R1', 10, 'Cogon Public Market')
ON DUPLICATE KEY UPDATE
    stop_name = VALUES(stop_name);

-- R2: Lumbia <-> Carmen
INSERT INTO route_stops (route_code, stop_order, stop_name) VALUES
    ('R2', 1, 'Centro Lumbia'),
    ('R2', 2, 'F. Delima St.'),
    ('R2', 3, 'Masterson Ave.'),
    ('R2', 4, 'Macapagal Drive'),
    ('R2', 5, 'SM Bypass Road'),
    ('R2', 6, 'C. Diel St.'),
    ('R2', 7, 'Villarin St.'),
    ('R2', 8, 'Max Suniel St.'),
    ('R2', 9, 'V. Neri St.'),
    ('R2', 10, 'Carmen Public Market')
ON DUPLICATE KEY UPDATE
    stop_name = VALUES(stop_name);
