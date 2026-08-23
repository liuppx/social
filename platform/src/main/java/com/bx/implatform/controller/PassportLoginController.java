package com.bx.implatform.controller;

import com.alibaba.fastjson.JSON;
import com.bx.implatform.result.Result;
import com.bx.implatform.result.ResultUtils;
import com.bx.implatform.service.PassportLoginService;
import com.bx.implatform.vo.LoginVO;
import com.bx.implatform.vo.PassportLoginSessionVO;
import com.bx.implatform.vo.PassportLoginStatusVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/passport")
public class PassportLoginController {
    private final PassportLoginService passportLoginService;

    @PostMapping("/login/session")
    public Result<PassportLoginSessionVO> createSession() {
        return ResultUtils.success(passportLoginService.createSession());
    }

    @PostMapping("/identity/login/session")
    public Result<PassportLoginSessionVO> createIdentitySession() {
        return ResultUtils.success(passportLoginService.createSession());
    }

    @PostMapping("/identity/login/verify")
    public Result<LoginVO> verifyIdentityLogin(@RequestBody Map<String, Object> request) {
        return ResultUtils.success(passportLoginService.verifyWalletPresentation(request));
    }

    @GetMapping("/login/status")
    public Result<PassportLoginStatusVO> getStatus(@RequestParam String sessionId) {
        return ResultUtils.success(passportLoginService.getStatus(sessionId));
    }

    @GetMapping("/callback")
    public void callback(@RequestParam String code, @RequestParam String state, HttpServletResponse response) throws IOException {
        passportLoginService.acceptCallback(code, state);
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(callbackHtml(state));
    }

    private String callbackHtml(String sessionId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "social-passport-callback");
        payload.put("sessionId", sessionId);
        payload.put("status", "approved");
        payload.put("time", System.currentTimeMillis());
        String payloadJson = JSON.toJSONString(payload);
        return """
            <!doctype html>
            <html lang="zh-CN">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>夜莺通行证</title>
                <style>
                    html,body{margin:0;width:100%;height:100%;background:#f8f8f8;color:#202124;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}
                    body{display:flex;align-items:center;justify-content:center}
                    .box{text-align:center;font-size:14px;line-height:22px;color:#606266}
                    .title{margin-bottom:8px;font-size:18px;font-weight:600;color:#202124}
                    button{margin-top:16px;border:0;background:#409eff;color:#fff;border-radius:4px;padding:8px 18px;cursor:pointer}
                </style>
            </head>
            <body>
                <div class="box">
                    <div class="title">登录确认成功</div>
                    <div>正在返回夜莺 Social...</div>
                    <button type="button" onclick="window.close()">关闭窗口</button>
                </div>
                <script>
                    (function () {
                        var payload = __PAYLOAD__;
                        var notify = function () {
                            try {
                                if (window.opener && !window.opener.closed) {
                                    window.opener.postMessage(JSON.stringify(payload), window.location.origin);
                                }
                            } catch (e) {}
                            try {
                                window.localStorage.setItem('__social_passport_callback__', JSON.stringify(payload));
                            } catch (e) {}
                            try {
                                var channel = new BroadcastChannel('social-passport-login');
                                channel.postMessage(payload);
                                channel.close();
                            } catch (e) {}
                        };
                        var closeWindow = function () {
                            try {
                                window.open('', '_self');
                            } catch (e) {}
                            try {
                                window.close();
                            } catch (e) {}
                        };
                        notify();
                        closeWindow();
                        setTimeout(closeWindow, 120);
                    })();
                </script>
            </body>
            </html>
            """.replace("__PAYLOAD__", payloadJson);
    }
}
