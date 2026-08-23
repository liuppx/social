package com.bx.implatform.service;

import com.bx.implatform.vo.LoginVO;
import com.bx.implatform.vo.PassportLoginSessionVO;
import com.bx.implatform.vo.PassportLoginStatusVO;

import java.util.Map;

public interface PassportLoginService {
    PassportLoginSessionVO createSession();
    PassportLoginStatusVO getStatus(String sessionId);
    void acceptCallback(String code, String state);
    LoginVO verifyWalletPresentation(Map<String, Object> request);
}
