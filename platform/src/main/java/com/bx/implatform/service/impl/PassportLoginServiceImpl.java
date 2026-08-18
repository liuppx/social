package com.bx.implatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bx.implatform.config.props.PassportProperties;
import com.bx.implatform.entity.PassportIdentity;
import com.bx.implatform.entity.User;
import com.bx.implatform.exception.GlobalException;
import com.bx.implatform.mapper.PassportIdentityMapper;
import com.bx.implatform.mapper.UserMapper;
import com.bx.implatform.service.LoginTokenService;
import com.bx.implatform.service.PassportLoginService;
import com.bx.implatform.vo.LoginVO;
import com.bx.implatform.vo.PassportLoginSessionVO;
import com.bx.implatform.vo.PassportLoginStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PassportLoginServiceImpl implements PassportLoginService {
    private static final String SESSION_KEY_PREFIX = "im:passport:login:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PassportProperties passportProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PassportIdentityMapper passportIdentityMapper;
    private final UserMapper userMapper;
    private final LoginTokenService loginTokenService;

    @Override
    public PassportLoginSessionVO createSession() {
        String nodeBaseUrl = required(passportProperties.getNodeBaseUrl(), "通行证登录未配置");
        String appId = required(passportProperties.getAppId(), "通行证应用 ID 未配置");
        String callbackUrl = required(passportProperties.getCallbackUrl(), "通行证回调地址未配置");
        String sessionId = UUID.randomUUID().toString();
        String verifier = randomBase64Url(64);
        String challenge = sha256Base64Url(verifier);
        Map<String, Object> payload = new HashMap<>();
        payload.put("appId", appId);
        payload.put("redirectUri", callbackUrl);
        payload.put("state", sessionId);
        payload.put("codeChallenge", challenge);
        payload.put("codeChallengeMethod", "S256");
        payload.put("scopes", passportProperties.getScopes());
        payload.put("requestTtlMs", passportProperties.getSessionTtlSeconds() * 1000L);
        Map<String, Object> data = nodeRequest(HttpMethod.POST, "/api/v1/public/auth/passport/authorize/request", payload);
        String requestId = string(data.get("requestId"));
        if (requestId.isEmpty()) {
            throw new GlobalException("通行证服务未返回授权请求");
        }

        Map<String, Object> session = new HashMap<>();
        session.put("requestId", requestId);
        session.put("codeVerifier", verifier);
        session.put("redirectUri", callbackUrl);
        session.put("appId", appId);
        redisTemplate.opsForValue().set(sessionKey(sessionId), session, Duration.ofSeconds(passportProperties.getSessionTtlSeconds()));

        PassportLoginSessionVO vo = new PassportLoginSessionVO();
        vo.setSessionId(sessionId);
        vo.setVerifyUrl(string(data.get("verifyUrl")));
        vo.setStatus(defaultString(data.get("status"), "pending"));
        vo.setExpiresAt(data.get("expiresAt") != null ? data.get("expiresAt") : data.get("expires_at"));
        vo.setPollInterval(2);
        return vo;
    }

    @Override
    public PassportLoginStatusVO getStatus(String sessionId) {
        Map<String, Object> session = getSession(sessionId);
        String code = string(session.get("authorizationCode"));
        if (code.isEmpty()) {
            Map<String, Object> request = nodeRequest(HttpMethod.GET,
                "/api/v1/public/auth/passport/authorize/request/" + string(session.get("requestId")), null);
            PassportLoginStatusVO vo = new PassportLoginStatusVO();
            String status = defaultString(request.get("status"), "pending").toLowerCase();
            vo.setStatus(status);
            vo.setMessage(string(request.get("message")));
            return vo;
        }
        Map<String, Object> exchange = new HashMap<>();
        exchange.put("code", code);
        exchange.put("appId", session.get("appId"));
        exchange.put("redirectUri", session.get("redirectUri"));
        exchange.put("codeVerifier", session.get("codeVerifier"));
        Map<String, Object> identity = nodeRequest(HttpMethod.POST, "/api/v1/public/auth/passport/authorize/exchange", exchange);
        LoginVO login = loginByIdentity(identity);
        redisTemplate.delete(sessionKey(sessionId));
        PassportLoginStatusVO vo = new PassportLoginStatusVO();
        vo.setStatus("approved");
        vo.setLogin(login);
        return vo;
    }

    @Override
    public void acceptCallback(String code, String state) {
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new GlobalException("通行证回调参数不完整");
        }
        Map<String, Object> session = getSession(state);
        if (!string(session.get("authorizationCode")).isEmpty()) {
            return;
        }
        session.put("authorizationCode", code);
        redisTemplate.opsForValue().set(sessionKey(state), session, Duration.ofSeconds(passportProperties.getSessionTtlSeconds()));
    }

    private LoginVO loginByIdentity(Map<String, Object> identity) {
        Map<String, Object> claims = asMap(identity.get("claims"));
        String subjectId = string(claims.get("subjectId"));
        if (subjectId.isEmpty()) {
            throw new GlobalException("通行证未返回身份标识");
        }
        PassportIdentity binding = passportIdentityMapper.selectOne(new LambdaQueryWrapper<PassportIdentity>()
            .eq(PassportIdentity::getSubjectId, subjectId));
        User user = binding == null ? null : userMapper.selectById(binding.getUserId());
        String walletAddress = string(claims.get("walletAddress")).toLowerCase();
        if (user == null && !walletAddress.isEmpty()) {
            user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getWalletAddress, walletAddress));
            if (user != null) {
                PassportIdentity migrated = new PassportIdentity();
                migrated.setSubjectId(subjectId);
                migrated.setUserId(user.getId());
                migrated.setWalletAddress(walletAddress);
                passportIdentityMapper.insert(migrated);
            }
        }
        if (user == null) {
            throw new GlobalException("该夜莺通行证尚未关联社区账号，请先使用已有账号登录后完成绑定");
        }
        if (Boolean.TRUE.equals(user.getIsBanned())) {
            throw new GlobalException("该社区账号已被禁用，无法登录");
        }
        applyVerifiedEmailClaim(user, claims);
        return loginTokenService.createToken(user, 0);
    }

    private void applyVerifiedEmailClaim(User user, Map<String, Object> claims) {
        String email = string(claims.get("email")).toLowerCase();
        if (!Boolean.parseBoolean(string(claims.get("emailVerified"))) || !email.contains("@")) {
            return;
        }
        if (email.equalsIgnoreCase(string(user.getEmail()))) {
            return;
        }
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (existing == null || existing.getId().equals(user.getId())) {
            User update = new User();
            update.setId(user.getId());
            update.setEmail(email);
            userMapper.updateById(update);
        }
    }

    private Map<String, Object> nodeRequest(HttpMethod method, String path, Map<String, Object> body) {
        String baseUrl = required(passportProperties.getNodeBaseUrl(), "通行证登录未配置").replaceAll("/+$", "");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        try {
            ResponseEntity<Map> response = new RestTemplate().exchange(baseUrl + path, method,
                new HttpEntity<>(body, headers), Map.class);
            Map<String, Object> envelope = response.getBody();
            if (envelope == null || !isSuccessful(envelope)) {
                throw new GlobalException(envelope == null ? "通行证服务返回异常" : defaultString(envelope.get("message"), "通行证服务请求失败"));
            }
            return asMap(envelope.get("data"));
        } catch (RestClientException exception) {
            log.warn("Passport request failed: {} {}", method, path, exception);
            throw new GlobalException("无法连接通行证服务，请稍后重试");
        }
    }

    private Map<String, Object> getSession(String sessionId) {
        Object value = redisTemplate.opsForValue().get(sessionKey(sessionId));
        Map<String, Object> session = asMap(value);
        if (session.isEmpty()) {
            throw new GlobalException("通行证登录二维码已过期，请重新发起登录");
        }
        return session;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? new HashMap<>((Map<String, Object>) value) : new HashMap<>();
    }

    private boolean isSuccessful(Map<String, Object> envelope) {
        Object code = envelope.get("code");
        return code instanceof Number && (((Number) code).intValue() == 0 || ((Number) code).intValue() == 200);
    }

    private String sessionKey(String sessionId) { return SESSION_KEY_PREFIX + sessionId; }
    private String required(String value, String message) { if (value == null || value.isBlank()) throw new GlobalException(message); return value.trim(); }
    private String string(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String defaultString(Object value, String fallback) { String result = string(value); return result.isEmpty() ? fallback : result; }
    private String randomBase64Url(int bytes) { byte[] value = new byte[bytes]; SECURE_RANDOM.nextBytes(value); return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private String sha256Base64Url(String value) { try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII))); } catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); } }
}
