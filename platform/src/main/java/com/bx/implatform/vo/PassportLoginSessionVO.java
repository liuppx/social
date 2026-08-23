package com.bx.implatform.vo;

import lombok.Data;

import java.util.List;

@Data
public class PassportLoginSessionVO {
    private String sessionId;
    private String requestId;
    private String appId;
    private String audience;
    private String nonce;
    private List<String> scopes;
    private String verifyUrl;
    private String status;
    private Object expiresAt;
    private int pollInterval;
}
