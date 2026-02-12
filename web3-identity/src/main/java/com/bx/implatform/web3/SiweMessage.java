package com.bx.implatform.web3;

import lombok.Data;

@Data
public class SiweMessage {

    private String domain;
    private String address;
    private String nonce;
    private String chainId;
    private String uri;
    private String issuedAt;
}
