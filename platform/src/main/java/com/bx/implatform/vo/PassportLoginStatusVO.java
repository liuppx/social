package com.bx.implatform.vo;

import lombok.Data;

@Data
public class PassportLoginStatusVO {
    private String status;
    private String message;
    private LoginVO login;
}
