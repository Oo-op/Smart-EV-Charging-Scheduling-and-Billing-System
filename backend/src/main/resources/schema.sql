CREATE DATABASE IF NOT EXISTS smart_charging DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE smart_charging;

-- =====================================================================
-- 1. 用户表：司机 / 管理员
-- =====================================================================
DROP TABLE IF EXISTS payment;
DROP TABLE IF EXISTS bill;
DROP TABLE IF EXISTS charging_session;
DROP TABLE IF EXISTS charging_request;
DROP TABLE IF EXISTS charging_pile;
DROP TABLE IF EXISTS vehicle;
DROP TABLE IF EXISTS app_user;
DROP TABLE IF EXISTS charging_station;

CREATE TABLE app_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    role VARCHAR(20) DEFAULT 'USER',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 2. 车辆表：属于某个用户
-- =====================================================================
CREATE TABLE vehicle (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    plate_number VARCHAR(20) NOT NULL,
    model VARCHAR(50),
    battery_capacity DECIMAL(10,2),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_plate (plate_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 3. 充电站（站点级别，便于聚合充电桩）
-- =====================================================================
CREATE TABLE charging_station (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    address VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 4. 充电桩表
-- =====================================================================
CREATE TABLE charging_pile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    station_id BIGINT,
    code VARCHAR(50) NOT NULL UNIQUE,
    mode VARCHAR(20) NOT NULL,
    power DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    service_fee DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_station (station_id),
    INDEX idx_mode (mode),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 5. 充电请求表（用户侧发起的请求，第一版排队从本表查）
--    排队查询：mode + status IN ('WAITING','ASSIGNED') + created_at
-- =====================================================================
CREATE TABLE charging_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    mode VARCHAR(20) NOT NULL,
    target_amount DECIMAL(10,2) NOT NULL,
    charged_amount DECIMAL(10,2) DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    queue_number INT,
    assigned_pile_id BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_vehicle (vehicle_id),
    INDEX idx_mode_status (mode, status),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 6. 充电会话表：一次插枪到拔枪
-- =====================================================================
CREATE TABLE charging_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id BIGINT,
    user_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    pile_id BIGINT NOT NULL,
    start_time DATETIME,
    end_time DATETIME,
    target_amount DECIMAL(10,2),
    charged_amount DECIMAL(10,2) DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'CHARGING',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_request (request_id),
    INDEX idx_user (user_id),
    INDEX idx_pile (pile_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 7. 账单表
-- =====================================================================
CREATE TABLE bill (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    electricity_fee DECIMAL(10,2) DEFAULT 0,
    service_fee DECIMAL(10,2) DEFAULT 0,
    total_fee DECIMAL(10,2) DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_session (session_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 8. 支付记录表
-- =====================================================================
CREATE TABLE payment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    bill_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    payment_method VARCHAR(30),
    amount DECIMAL(10,2),
    status VARCHAR(20),
    paid_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_bill (bill_id),
    INDEX idx_user (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 9. 电价策略表（按时间段与充电模式）
--    period: PEAK / FLAT / VALLEY        峰 / 平 / 谷
--    mode  : FAST / NORMAL / SLOW         快充 / 普通 / 慢充
--    电费 + 服务费 = charging_fee + service_fee
-- =====================================================================
CREATE TABLE IF NOT EXISTS electricity_price (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    period VARCHAR(20) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    charging_fee DECIMAL(10,4) NOT NULL COMMENT '元/度',
    service_fee DECIMAL(10,4) NOT NULL COMMENT '元/度',
    description VARCHAR(128),
    UNIQUE KEY uk_period_mode (period, mode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 默认电价：峰 / 平 / 谷 × 快充 / 慢充 / 普通
INSERT INTO electricity_price (period, mode, charging_fee, service_fee, description) VALUES
    ('PEAK',   'FAST',   1.20, 0.70, '08:00-11:00, 18:00-21:00'),
    ('FLAT',   'FAST',   0.80, 0.70, '12:00-17:00'),
    ('VALLEY', 'FAST',   0.40, 0.70, '22:00-07:00'),
    ('PEAK',   'SLOW',   1.20, 0.40, '08:00-11:00, 18:00-21:00'),
    ('FLAT',   'SLOW',   0.80, 0.40, '12:00-17:00'),
    ('VALLEY', 'SLOW',   0.40, 0.40, '22:00-07:00'),
    ('PEAK',   'NORMAL', 1.00, 0.50, '08:00-11:00, 18:00-21:00'),
    ('FLAT',   'NORMAL', 0.70, 0.50, '12:00-17:00'),
    ('VALLEY', 'NORMAL', 0.30, 0.50, '22:00-07:00');
