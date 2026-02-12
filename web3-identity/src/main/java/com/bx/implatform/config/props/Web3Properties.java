package com.bx.implatform.config.props;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class Web3Properties {

    @Value("${web3.auth.nonceExpireIn:300}")
    private Integer nonceExpireIn;

    @Value("${web3.auth.autoRegister:true}")
    private Boolean autoRegister;

    @Value("${web3.auth.defaultChainId:1}")
    private String defaultChainId;

    @Value("${web3.auth.expectedDomain:}")
    private String expectedDomain;

    @Value("${web3.did.enabled:false}")
    private Boolean didEnabled;

    @Value("${web3.ucan.audience:}")
    private String ucanAudience;

    @Value("${web3.ucan.resource:profile}")
    private String ucanResource;

    @Value("${web3.ucan.action:read}")
    private String ucanAction;
}
