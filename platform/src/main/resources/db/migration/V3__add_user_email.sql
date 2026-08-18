ALTER TABLE t_user
    ADD COLUMN email VARCHAR(320) NULL COMMENT '账户邮箱' AFTER user_name;

CREATE UNIQUE INDEX uk_user_email ON t_user (email);
