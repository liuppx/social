package com.bx.implatform.controller;

import com.bx.implatform.result.Result;
import com.bx.implatform.result.ResultUtils;
import com.bx.implatform.service.PassportLoginService;
import com.bx.implatform.vo.PassportLoginSessionVO;
import com.bx.implatform.vo.PassportLoginStatusVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/passport")
public class PassportLoginController {
    private final PassportLoginService passportLoginService;

    @PostMapping("/login/session")
    public Result<PassportLoginSessionVO> createSession() {
        return ResultUtils.success(passportLoginService.createSession());
    }

    @GetMapping("/login/status")
    public Result<PassportLoginStatusVO> getStatus(@RequestParam String sessionId) {
        return ResultUtils.success(passportLoginService.getStatus(sessionId));
    }

    @GetMapping("/callback")
    public void callback(@RequestParam String code, @RequestParam String state, HttpServletResponse response) throws IOException {
        passportLoginService.acceptCallback(code, state);
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write("<!doctype html><title>夜莺社区</title><p>登录确认成功，请返回夜莺社区。</p>");
    }
}
