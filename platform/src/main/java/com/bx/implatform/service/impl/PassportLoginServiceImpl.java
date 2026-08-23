package com.bx.implatform.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bx.implatform.config.props.PassportProperties;
import com.bx.implatform.entity.User;
import com.bx.implatform.entity.WalletIdentity;
import com.bx.implatform.exception.GlobalException;
import com.bx.implatform.mapper.UserMapper;
import com.bx.implatform.mapper.WalletIdentityMapper;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
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
    private final WalletIdentityMapper walletIdentityMapper;
    private final UserMapper userMapper;
    private final LoginTokenService loginTokenService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PassportLoginSessionVO createSession() {
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
        Map<String, Object> data = nodeRequest(HttpMethod.POST, "/api/v1/public/identity/authorize/request", payload);
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
        vo.setRequestId(requestId);
        vo.setAppId(appId);
        vo.setAudience(string(data.get("audience")));
        vo.setNonce(string(data.get("nonce")));
        vo.setScopes(passportProperties.getScopes());
        vo.setVerifyUrl(resolveNodeUrl(string(data.get("verifyUrl"))));
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
                "/api/v1/public/identity/authorize/request/" + string(session.get("requestId")), null);
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
        Map<String, Object> identity = nodeRequest(HttpMethod.POST, "/api/v1/public/identity/authorize/exchange", exchange);
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

    @Override
    public LoginVO verifyWalletPresentation(Map<String, Object> request) {
        String sessionId = firstString(request, "sessionId", "session_id");
        Map<String, Object> session = getSession(sessionId);
        String requestId = string(session.get("requestId"));
        String submittedRequestId = firstString(request, "requestId", "request_id");
        if (!submittedRequestId.isEmpty() && !requestId.equals(submittedRequestId)) {
            throw new GlobalException("钱包身份登录会话不匹配");
        }
        Object presentation = request.get("presentation");
        if (presentation == null) {
            throw new GlobalException("钱包身份授权结果为空");
        }

        Map<String, Object> approve = new HashMap<>();
        approve.put("requestId", requestId);
        approve.put("presentation", presentation);
        Map<String, Object> approved = nodeRequest(HttpMethod.POST, "/api/v1/public/identity/authorize/approve", approve);
        String code = string(approved.get("authorizationCode"));
        if (code.isEmpty()) {
            throw new GlobalException("钱包身份服务未返回授权码");
        }
        Map<String, Object> exchange = new HashMap<>();
        exchange.put("code", code);
        exchange.put("appId", session.get("appId"));
        exchange.put("redirectUri", session.get("redirectUri"));
        exchange.put("codeVerifier", session.get("codeVerifier"));
        Map<String, Object> identity = nodeRequest(HttpMethod.POST, "/api/v1/public/identity/authorize/exchange", exchange);
        LoginVO login = loginByIdentity(identity);
        redisTemplate.delete(sessionKey(sessionId));
        return login;
    }

    private LoginVO loginByIdentity(Map<String, Object> identity) {
        String did = string(identity.get("did"));
        if (did.isEmpty()) {
            did = string(identity.get("walletIdentityId"));
        }
        if (did.isEmpty()) {
            throw new GlobalException("钱包身份服务未返回 DID");
        }
        WalletIdentity binding = walletIdentityMapper.selectOne(new LambdaQueryWrapper<WalletIdentity>()
            .eq(WalletIdentity::getWalletIdentityDid, did));
        User user = binding == null ? null : userMapper.selectById(binding.getUserId());
        String walletAddress = string(identity.get("walletAddress")).toLowerCase();
        if (user == null && !walletAddress.isEmpty()) {
            user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getWalletAddress, walletAddress));
            if (user != null) {
                bindIdentity(did, user.getId(), walletAddress);
            }
        }
        String email = extractVerifiedEmail(identity);
        if (user == null && !email.isEmpty()) {
            user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        }
        if (user == null) {
            if (email.isEmpty()) {
                throw new GlobalException("Social 需要已验证邮箱。请先在夜莺钱包插件中完成钱包身份验证和邮箱验证，再重新登录。");
            }
            user = createIdentityUser(did, walletAddress, email);
        }
        if (Boolean.TRUE.equals(user.getIsBanned())) {
            throw new GlobalException("该社区账号已被禁用，无法登录");
        }
        applyIdentityClaims(user, did, walletAddress, email);
        bindIdentity(did, user.getId(), walletAddress);
        return loginTokenService.createToken(user, 0);
    }

    private User createIdentityUser(String did, String walletAddress, String email) {
        User user = new User();
        user.setEmail(email);
        user.setUserName(generateUniqueUserName(email));
        user.setNickName(email.substring(0, email.indexOf('@')));
        user.setPassword(passwordEncoder.encode(randomBase64Url(32)));
        user.setDid(did);
        user.setWalletAddress(walletAddress);
        user.setWalletType(walletAddress.isEmpty() ? "" : "eip155");
        userMapper.insert(user);
        log.info("Auto registered social user by wallet identity, userId:{}, did:{}, email:{}", user.getId(), did, email);
        return user;
    }

    private void applyIdentityClaims(User user, String did, String walletAddress, String email) {
        User update = new User();
        update.setId(user.getId());
        boolean changed = false;
        if (!did.isEmpty() && !did.equals(string(user.getDid()))) {
            update.setDid(did);
            changed = true;
        }
        if (!walletAddress.isEmpty() && !walletAddress.equalsIgnoreCase(string(user.getWalletAddress()))) {
            update.setWalletAddress(walletAddress);
            update.setWalletType("eip155");
            changed = true;
        }
        if (!email.isEmpty() && !email.equalsIgnoreCase(string(user.getEmail()))) {
            User existing = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
            if (existing == null || existing.getId().equals(user.getId())) {
                update.setEmail(email);
                changed = true;
            }
        }
        if (changed) {
            userMapper.updateById(update);
        }
    }

    private void bindIdentity(String did, Long userId, String walletAddress) {
        WalletIdentity existing = walletIdentityMapper.selectOne(new LambdaQueryWrapper<WalletIdentity>()
            .eq(WalletIdentity::getWalletIdentityDid, did));
        if (existing != null) {
            return;
        }
        WalletIdentity binding = new WalletIdentity();
        binding.setWalletIdentityDid(did);
        binding.setUserId(userId);
        binding.setWalletAddress(walletAddress);
        walletIdentityMapper.insert(binding);
    }

    @SuppressWarnings("unchecked")
    private String extractVerifiedEmail(Map<String, Object> identity) {
        Object credentials = identity.get("credentials");
        if (!(credentials instanceof List<?> list)) {
            return "";
        }
        for (Object item : list) {
            Map<String, Object> credential = item instanceof Map ? (Map<String, Object>) item : new HashMap<>();
            if (!"EmailCredential".equals(string(credential.get("type")))) {
                continue;
            }
            String token = string(credential.get("credential"));
            String email = emailFromCredential(token);
            if (!email.isEmpty()) {
                return email;
            }
        }
        return "";
    }

    private String emailFromCredential(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return "";
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JSONObject object = JSON.parseObject(payload);
            JSONObject subject = object.getJSONObject("vc").getJSONObject("credentialSubject");
            String email = string(subject.get("email")).toLowerCase();
            return email.contains("@") ? email : "";
        } catch (Exception exception) {
            return "";
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
                throw new GlobalException(envelope == null ? "钱包身份服务返回异常" : identityErrorMessage(defaultString(envelope.get("message"), "钱包身份服务请求失败")));
            }
            return asMap(envelope.get("data"));
        } catch (HttpStatusCodeException exception) {
            log.warn("Identity request rejected: {} {} {}", method, path, exception.getStatusCode());
            throw new GlobalException(identityErrorMessage(identityResponseMessage(exception.getResponseBodyAsString())));
        } catch (RestClientException exception) {
            log.warn("Identity request failed: {} {}", method, path, exception);
            throw new GlobalException("无法连接钱包身份服务，请稍后重试");
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

    private String resolveNodeUrl(String value) {
        if (value.isEmpty() || value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        String baseUrl = required(passportProperties.getNodeBaseUrl(), "通行证登录未配置").replaceAll("/+$", "");
        return baseUrl + (value.startsWith("/") ? value : "/" + value);
    }

    private String identityErrorMessage(String message) {
        return switch (message) {
            case "IDENTITY_EMAIL_REQUIRED" -> "Social 需要已验证邮箱。请先在夜莺钱包插件中完成钱包身份验证和邮箱验证，再重新登录。";
            case "IDENTITY_WALLET_ACCOUNT_REQUIRED" -> "当前钱包身份缺少已验证钱包账户，请先在夜莺钱包插件中完成钱包身份验证。";
            case "IDENTITY_AUTHORIZATION_REQUEST_EXPIRED" -> "钱包身份登录请求已过期，请重新发起登录。";
            case "IDENTITY_PRESENTATION_SCOPE_INVALID", "IDENTITY_SCOPE_INVALID" -> "钱包身份授权范围不满足 Social 登录要求。";
            default -> message;
        };
    }

    private String identityResponseMessage(String body) {
        try {
            JSONObject envelope = JSON.parseObject(body);
            String message = defaultString(envelope.get("message"), "");
            if (message.isEmpty()) {
                message = defaultString(envelope.get("reason"), "");
            }
            return message.isEmpty() ? "钱包身份服务请求失败" : message;
        } catch (Exception exception) {
            return "钱包身份服务请求失败";
        }
    }

    private String firstString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String value = string(map.get(key));
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private String generateUniqueUserName(String email) {
        String prefix = email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9_]", "_");
        if (prefix.isBlank()) {
            prefix = "user";
        }
        prefix = prefix.length() > 14 ? prefix.substring(0, 14) : prefix;
        String candidate = prefix;
        int suffix = 1;
        while (userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUserName, candidate)) != null) {
            candidate = prefix + "_" + suffix++;
        }
        return candidate;
    }

    private String sessionKey(String sessionId) { return SESSION_KEY_PREFIX + sessionId; }
    private String required(String value, String message) { if (value == null || value.isBlank()) throw new GlobalException(message); return value.trim(); }
    private String string(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String defaultString(Object value, String fallback) { String result = string(value); return result.isEmpty() ? fallback : result; }
    private String randomBase64Url(int bytes) { byte[] value = new byte[bytes]; SECURE_RANDOM.nextBytes(value); return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private String sha256Base64Url(String value) { try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII))); } catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); } }
}
