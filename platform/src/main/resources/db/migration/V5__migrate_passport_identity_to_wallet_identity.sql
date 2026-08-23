CREATE TABLE IF NOT EXISTS t_wallet_identity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    wallet_identity_did VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    wallet_address VARCHAR(64) NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_identity_did (wallet_identity_did),
    KEY idx_wallet_identity_user (user_id),
    KEY idx_wallet_identity_wallet_address (wallet_address)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO t_wallet_identity (
    wallet_identity_did,
    user_id,
    wallet_address,
    created_time,
    updated_time
)
SELECT
    subject_id,
    user_id,
    wallet_address,
    created_time,
    updated_time
FROM t_passport_identity
WHERE subject_id IS NOT NULL
  AND subject_id <> ''
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    wallet_address = VALUES(wallet_address),
    updated_time = VALUES(updated_time);

DROP TABLE IF EXISTS t_passport_identity;
