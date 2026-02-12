package com.bx.implatform.controller;

import com.alibaba.fastjson.JSON;
import com.bx.imcommon.util.JwtUtil;
import com.bx.implatform.config.props.JwtProperties;
import com.bx.implatform.dto.Web3ChallengeDTO;
import com.bx.implatform.dto.Web3VerifyDTO;
import com.bx.implatform.entity.User;
import com.bx.implatform.exception.GlobalException;
import com.bx.implatform.mapper.UserMapper;
import com.bx.implatform.result.Result;
import com.bx.implatform.result.ResultUtils;
import com.bx.implatform.service.Web3AuthService;
import com.bx.implatform.session.UserSession;
import com.bx.implatform.vo.LoginVO;
import com.bx.implatform.vo.Web3ChallengeVO;
import com.bx.implatform.vo.Web3ProfileVO;
import com.bx.implatform.vo.Web3VerifyVO;
import com.bx.implatform.web3.UcanVerifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Tag(name = "Web3 Public Auth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/auth")
public class Web3PublicAuthController {

    private final Web3AuthService web3AuthService;
    private final JwtProperties jwtProperties;
    private final UserMapper userMapper;
    private final UcanVerifier ucanVerifier;

    @PostMapping("/challenge")
    @Operation(summary = "Issue challenge", description = "Issue a login challenge for wallet signature")
    public Result<Web3ChallengeVO> challenge(@Valid @RequestBody Web3ChallengeDTO dto) {
        return ResultUtils.success(web3AuthService.issueChallenge(dto));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify challenge", description = "Verify signature and issue access token")
    public Result<Web3VerifyVO> verify(@Valid @RequestBody Web3VerifyDTO dto) {
        return ResultUtils.success(web3AuthService.verifyChallenge(dto));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Refresh access token")
    public Result<Web3VerifyVO> refresh(HttpServletRequest request) {
        String refreshToken = request.getHeader("refreshToken");
        if (refreshToken == null || refreshToken.isEmpty()) {
            refreshToken = readCookie(request, "refresh_token");
        }
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new GlobalException("missing refresh token");
        }
        LoginVO login = web3AuthService.refreshToken(refreshToken);
        Web3VerifyVO vo = new Web3VerifyVO();
        vo.setToken(login.getAccessToken());
        vo.setRefreshToken(login.getRefreshToken());
        vo.setExpiresAt(System.currentTimeMillis() + login.getAccessTokenExpiresIn() * 1000L);
        vo.setRefreshExpiresAt(System.currentTimeMillis() + login.getRefreshTokenExpiresIn() * 1000L);
        return ResultUtils.success(vo);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Logout and clear session")
    public Result<Web3ProfileVO> logout() {
        return ResultUtils.success();
    }

    @GetMapping("/profile")
    @Operation(summary = "Profile", description = "Validate token and return profile")
    public Result<Web3ProfileVO> profile(HttpServletRequest request) {
        String token = extractBearerToken(request);
        if (ucanVerifier.isUcanToken(token)) {
            String address = ucanVerifier.verifyInvocation(token);
            Web3ProfileVO vo = new Web3ProfileVO();
            vo.setAddress(address);
            vo.setIssuedAt(System.currentTimeMillis());
            return ResultUtils.success(vo);
        }
        if (!JwtUtil.checkSign(token, jwtProperties.getAccessTokenSecret())) {
            throw new GlobalException("token expired");
        }
        UserSession session = JSON.parseObject(JwtUtil.getInfo(token), UserSession.class);
        User user = userMapper.selectById(session.getUserId());
        Web3ProfileVO vo = new Web3ProfileVO();
        vo.setIssuedAt(System.currentTimeMillis());
        if (user != null) {
            vo.setAddress(user.getWalletAddress());
        }
        return ResultUtils.success(vo);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.toLowerCase().startsWith("bearer ")) {
            throw new GlobalException("missing access token");
        }
        return auth.substring(7).trim();
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        Optional<Cookie> cookie = java.util.Arrays.stream(request.getCookies())
            .filter(c -> name.equals(c.getName()))
            .findFirst();
        return cookie.map(Cookie::getValue).orElse(null);
    }
}
