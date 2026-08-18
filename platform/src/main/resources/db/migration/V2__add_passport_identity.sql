CREATE TABLE IF NOT EXISTS t_passport_identity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    subject_id VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    wallet_address VARCHAR(64) NULL,
    device_name VARCHAR(255) NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_passport_identity_subject (subject_id),
    KEY idx_passport_identity_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
