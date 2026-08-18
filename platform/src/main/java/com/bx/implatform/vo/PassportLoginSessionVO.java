package com.bx.implatform.vo;

import lombok.Data;

@Data
public class PassportLoginSessionVO {
    private String sessionId;
    private String verifyUrl;
    private String status;
    private Object expiresAt;
    private int pollInterval;
}
