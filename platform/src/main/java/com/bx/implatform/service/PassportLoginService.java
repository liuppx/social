package com.bx.implatform.service;

import com.bx.implatform.vo.PassportLoginSessionVO;
import com.bx.implatform.vo.PassportLoginStatusVO;

public interface PassportLoginService {
    PassportLoginSessionVO createSession();
    PassportLoginStatusVO getStatus(String sessionId);
    void acceptCallback(String code, String state);
}
