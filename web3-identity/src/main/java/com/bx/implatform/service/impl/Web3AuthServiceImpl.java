package com.bx.implatform.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bx.implatform.config.props.Web3Properties;
import com.bx.implatform.contant.RedisKey;
import com.bx.implatform.dto.SiweNonceDTO;
import com.bx.implatform.dto.SiweVerifyDTO;
import com.bx.implatform.dto.WalletLinkDTO;
import com.bx.implatform.dto.WalletUnlinkDTO;
import com.bx.implatform.dto.Web3ChallengeDTO;
import com.bx.implatform.dto.Web3VerifyDTO;
import com.bx.implatform.entity.User;
import com.bx.implatform.entity.PassportIdentity;
import com.bx.implatform.entity.Wallet;
import com.bx.implatform.exception.GlobalException;
import com.bx.implatform.mapper.UserMapper;
import com.bx.implatform.mapper.PassportIdentityMapper;
import com.bx.implatform.service.LoginTokenService;
import com.bx.implatform.service.WalletService;
import com.bx.implatform.service.Web3AuthService;
import com.bx.implatform.session.UserSession;
import com.bx.implatform.vo.LoginVO;
import com.bx.implatform.vo.SiweNonceVO;
import com.bx.implatform.vo.Web3ChallengeVO;
import com.bx.implatform.vo.Web3VerifyVO;
import com.bx.implatform.web3.SiweMessage;
import com.bx.implatform.web3.SiweMessageParser;
import com.bx.implatform.web3.Web3SignatureVerifier;
import com.bx.implatform.config.props.JwtProperties;
import com.bx.imcommon.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class Web3AuthServiceImpl implements Web3AuthService {

    private static final String CHAIN_TYPE_EVM = "EVM";

    private final RedisTemplate<String, Object> redisTemplate;
    private final Web3Properties web3Properties;
    private final JwtProperties jwtProperties;
    private final LoginTokenService loginTokenService;
    private final UserMapper userMapper;
    private final WalletService walletService;
    private final PasswordEncoder passwordEncoder;
    private final PassportIdentityMapper passportIdentityMapper;

    private static final List<String> COMMUNITY_SCOPES = List.of("identity.basic", "identity.wallet", "identity.username", "identity.email");

    @Override
    public SiweNonceVO issueNonce(SiweNonceDTO dto) {
        String address = normalizeAddress(dto.getAddress());
        String chainId = normalizeChainId(dto.getChainId());
        String nonce = RandomUtil.randomString(16);
        String key = buildNonceKey(address, chainId);
        redisTemplate.opsForValue().set(key, nonce, web3Properties.getNonceExpireIn(), TimeUnit.SECONDS);
        SiweNonceVO vo = new SiweNonceVO();
        vo.setNonce(nonce);
        vo.setExpiresIn(web3Properties.getNonceExpireIn());
        vo.setChainId(chainId);
        return vo;
    }

    @Override
    public Web3ChallengeVO issueChallenge(Web3ChallengeDTO dto) {
        String address = normalizeAddress(dto.getAddress());
        String chainId = normalizeChainId(dto.getChainId());
        String nonce = RandomUtil.randomString(16);
        long issuedAt = System.currentTimeMillis();
        long expiresAt = issuedAt + web3Properties.getNonceExpireIn() * 1000L;
        String challenge = buildChallenge(address, nonce, issuedAt, chainId);
        String key = buildChallengeKey(address);
        List<String> scopes = normalizeScopes(dto.getScope());
        String appId = requiredPassportConfig(web3Properties.getPassportAppId(), "通行证应用 ID 未配置");
        String audience = requiredPassportConfig(web3Properties.getPassportAudience(), "通行证访问方未配置");
        String endpoint = requiredPassportConfig(web3Properties.getPassportNodeBaseUrl(), "通行证服务未配置");
        if (dto.getAppId() != null && !dto.getAppId().isBlank() && !appId.equals(dto.getAppId().trim())) {
            throw new GlobalException("通行证应用 ID 不匹配");
        }
        Map<String, Object> loginRequest = new HashMap<>();
        loginRequest.put("challenge", challenge);
        loginRequest.put("nonce", nonce);
        loginRequest.put("scope", scopes);
        loginRequest.put("appId", appId);
        loginRequest.put("audience", audience);
        redisTemplate.opsForValue().set(key, loginRequest, web3Properties.getNonceExpireIn(), TimeUnit.SECONDS);
        Web3ChallengeVO vo = new Web3ChallengeVO();
        vo.setChallenge(challenge);
        vo.setNonce(nonce);
        vo.setIssuedAt(issuedAt);
        vo.setExpiresAt(expiresAt);
        vo.setChainId(chainId);
        vo.setAppId(appId);
        vo.setAudience(audience);
        vo.setScope(scopes);
        vo.setPassportEndpoint(endpoint.replaceAll("/+$", ""));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public LoginVO verifyAndLogin(SiweVerifyDTO dto) {
        SiweMessage siwe = verifySiwe(dto.getAddress(), dto.getMessage(), dto.getSignature());
        String address = normalizeAddress(dto.getAddress());
        User user = resolveUserByWallet(address, siwe.getChainId());
        return loginTokenService.createToken(user, dto.getTerminal());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Web3VerifyVO verifyChallenge(Web3VerifyDTO dto) {
        String address = normalizeAddress(dto.getAddress());
        String chainId = normalizeChainId(null);
        String key = buildChallengeKey(address);
        Object storedChallenge = redisTemplate.opsForValue().get(key);
        if (!(storedChallenge instanceof Map)) {
            throw new GlobalException("挑战已过期，请重新获取");
        }
        Map<String, Object> loginRequest = (Map<String, Object>) storedChallenge;
        String challenge = String.valueOf(loginRequest.get("challenge"));
        if (!Web3SignatureVerifier.verifyPersonalSign(challenge, dto.getSignature(), address)) {
            throw new GlobalException("签名校验失败");
        }
        Map<String, Object> assertion = validatePassportAssertion(dto, address, loginRequest);
        redisTemplate.delete(key);
        User user = resolvePassportUser(assertion, address, chainId);
        applyPassportEmail(user, assertion);
        Integer terminal = dto.getTerminal() == null ? 0 : dto.getTerminal();
        LoginVO login = loginTokenService.createToken(user, terminal);
        Web3VerifyVO vo = new Web3VerifyVO();
        vo.setToken(login.getAccessToken());
        vo.setRefreshToken(login.getRefreshToken());
        vo.setAddress(address);
        vo.setExpiresAt(System.currentTimeMillis() + login.getAccessTokenExpiresIn() * 1000L);
        vo.setRefreshExpiresAt(System.currentTimeMillis() + login.getRefreshTokenExpiresIn() * 1000L);
        return vo;
    }

    @Override
    public LoginVO refreshToken(String refreshToken) {
        if (!JwtUtil.checkSign(refreshToken, jwtProperties.getRefreshTokenSecret())) {
            throw new GlobalException("刷新令牌无效");
        }
        Long userId = JwtUtil.getUserId(refreshToken);
        String strJson = JwtUtil.getInfo(refreshToken);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new GlobalException("用户不存在");
        }
        if (Boolean.TRUE.equals(user.getIsBanned())) {
            String tip = String.format("您的账号因'%s'被管理员封禁,请联系客服!", user.getReason());
            throw new GlobalException(tip);
        }
        String accessToken =
            JwtUtil.sign(userId, strJson, jwtProperties.getAccessTokenExpireIn(), jwtProperties.getAccessTokenSecret());
        String newRefreshToken = JwtUtil.sign(userId, strJson, jwtProperties.getRefreshTokenExpireIn(),
            jwtProperties.getRefreshTokenSecret());
        LoginVO vo = new LoginVO();
        vo.setAccessToken(accessToken);
        vo.setAccessTokenExpiresIn(jwtProperties.getAccessTokenExpireIn());
        vo.setRefreshToken(newRefreshToken);
        vo.setRefreshTokenExpiresIn(jwtProperties.getRefreshTokenExpireIn());
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void linkWallet(UserSession session, WalletLinkDTO dto) {
        SiweMessage siwe = verifySiwe(dto.getAddress(), dto.getMessage(), dto.getSignature());
        String address = normalizeAddress(dto.getAddress());
        Wallet existing = walletService.findByAddress(address, CHAIN_TYPE_EVM);
        if (existing != null && !Objects.equals(existing.getUserId(), session.getUserId())) {
            throw new GlobalException("该钱包已绑定到其他账号");
        }
        if (existing == null) {
            Wallet wallet = new Wallet();
            wallet.setUserId(session.getUserId());
            wallet.setAddress(address);
            wallet.setChainType(CHAIN_TYPE_EVM);
            wallet.setIsPrimary(false);
            walletService.save(wallet);
            existing = wallet;
        }
        User user = userMapper.selectById(session.getUserId());
        if (user == null) {
            throw new GlobalException("用户不存在");
        }
        if (StrUtil.isBlank(user.getWalletAddress())) {
            setPrimaryWallet(user.getId(), existing, siwe.getChainId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void unlinkWallet(UserSession session, WalletUnlinkDTO dto) {
        String address = normalizeAddress(dto.getAddress());
        Wallet wallet = walletService.findByAddress(address, CHAIN_TYPE_EVM);
        if (wallet == null || !Objects.equals(wallet.getUserId(), session.getUserId())) {
            throw new GlobalException("未找到该钱包绑定关系");
        }
        walletService.removeById(wallet.getId());
        User user = userMapper.selectById(session.getUserId());
        if (user == null) {
            throw new GlobalException("用户不存在");
        }
        if (Boolean.TRUE.equals(wallet.getIsPrimary())) {
            List<Wallet> wallets = walletService.findByUserId(session.getUserId());
            if (wallets.isEmpty()) {
                User update = new User();
                update.setId(user.getId());
                update.setWalletAddress("");
                update.setWalletType("");
                update.setWalletVerifiedAt(null);
                if (Boolean.TRUE.equals(web3Properties.getDidEnabled())) {
                    update.setDid("");
                }
                userMapper.updateById(update);
            } else {
                setPrimaryWallet(user.getId(), wallets.get(0), web3Properties.getDefaultChainId());
            }
        }
    }

    private SiweMessage verifySiwe(String address, String message, String signature) {
        SiweMessage siwe = SiweMessageParser.parse(message);
        if (siwe == null || StrUtil.isBlank(siwe.getNonce()) || StrUtil.isBlank(siwe.getAddress())) {
            throw new GlobalException("SIWE消息格式不正确");
        }
        String normalizedAddress = normalizeAddress(address);
        if (!normalizedAddress.equalsIgnoreCase(normalizeAddress(siwe.getAddress()))) {
            throw new GlobalException("SIWE地址不一致");
        }
        String expectedDomain = web3Properties.getExpectedDomain();
        if (StrUtil.isNotBlank(expectedDomain) && !expectedDomain.equalsIgnoreCase(siwe.getDomain())) {
            throw new GlobalException("SIWE域名校验失败");
        }
        String chainId = normalizeChainId(siwe.getChainId());
        String key = buildNonceKey(normalizedAddress, chainId);
        Object storedNonce = redisTemplate.opsForValue().get(key);
        if (storedNonce == null || !siwe.getNonce().equals(storedNonce.toString())) {
            throw new GlobalException("SIWE nonce无效或已过期");
        }
        if (!Web3SignatureVerifier.verifyPersonalSign(message, signature, normalizedAddress)) {
            throw new GlobalException("SIWE签名校验失败");
        }
        redisTemplate.delete(key);
        siwe.setChainId(chainId);
        return siwe;
    }

    private User resolveUserByWallet(String address, String chainId) {
        Wallet wallet = walletService.findByAddress(address, CHAIN_TYPE_EVM);
        User user = null;
        if (wallet != null) {
            user = userMapper.selectById(wallet.getUserId());
        }
        if (user == null) {
            if (!Boolean.TRUE.equals(web3Properties.getAutoRegister())) {
                throw new GlobalException("钱包未绑定，请先绑定后再登录");
            }
            user = createUserForWallet(address, chainId);
            Wallet newWallet = new Wallet();
            newWallet.setUserId(user.getId());
            newWallet.setAddress(address);
            newWallet.setChainType(CHAIN_TYPE_EVM);
            newWallet.setIsPrimary(true);
            walletService.save(newWallet);
        }
        return user;
    }

    private List<String> normalizeScopes(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return COMMUNITY_SCOPES;
        }
        List<String> scopes = new ArrayList<>();
        for (String value : requested) {
            if (value != null && !value.isBlank() && !scopes.contains(value.trim())) scopes.add(value.trim());
        }
        if (!scopes.containsAll(COMMUNITY_SCOPES)) {
            throw new GlobalException("社区登录必须请求基础身份、钱包和已验证邮箱");
        }
        return scopes;
    }

    private Map<String, Object> validatePassportAssertion(Web3VerifyDTO dto, String address, Map<String, Object> request) {
        if (dto.getPassportAssertion() == null || dto.getPassportAssertion().isBlank() || dto.getWalletProof() == null) {
            throw new GlobalException("需要夜莺通行证钱包身份声明");
        }
        Map<String, Object> result = passportClaims(dto.getPassportAssertion());
        if (!Boolean.TRUE.equals(result.get("active"))) throw new GlobalException("夜莺通行证身份声明无效");
        Map<String, Object> claims = asMap(result.get("claims"));
        List<String> requiredScopes = (List<String>) request.get("scope");
        List<String> actualScopes = stringList(claims.get("scope"));
        if (string(claims.get("subjectId")).isBlank() || !address.equalsIgnoreCase(string(claims.get("walletAddress"))) ||
            !string(request.get("nonce")).equals(string(claims.get("nonce"))) ||
            !string(request.get("appId")).equals(string(claims.get("appId"))) ||
            !string(request.get("audience")).equals(string(claims.get("aud"))) || !actualScopes.containsAll(requiredScopes) ||
            !Boolean.parseBoolean(string(claims.get("emailVerified"))) || string(claims.get("email")).isBlank()) {
            throw new GlobalException("需要已验证的夜莺通行证邮箱");
        }
        if (!address.equalsIgnoreCase(string(dto.getWalletProof().get("address"))) ||
            !string(request.get("nonce")).equals(string(dto.getWalletProof().get("nonce"))) ||
            !string(request.get("appId")).equals(string(dto.getWalletProof().get("appId"))) ||
            !string(request.get("audience")).equals(string(dto.getWalletProof().get("audience"))) ||
            !stringList(dto.getWalletProof().get("scopes")).containsAll(requiredScopes)) {
            throw new GlobalException("夜莺通行证钱包证明无效");
        }
        return result;
    }

    private User resolvePassportUser(Map<String, Object> assertion, String address, String chainId) {
        Map<String, Object> claims = asMap(assertion.get("claims"));
        String subjectId = string(claims.get("subjectId"));
        PassportIdentity identity = passportIdentityMapper.selectOne(new LambdaQueryWrapper<PassportIdentity>()
            .eq(PassportIdentity::getSubjectId, subjectId));
        User user = identity == null ? null : userMapper.selectById(identity.getUserId());
        if (user == null) {
            user = resolveUserByWallet(address, chainId);
            PassportIdentity existing = passportIdentityMapper.selectOne(new LambdaQueryWrapper<PassportIdentity>()
                .eq(PassportIdentity::getUserId, user.getId()));
            if (existing != null && !subjectId.equals(existing.getSubjectId())) {
                throw new GlobalException("该社区账号已关联其他夜莺通行证");
            }
            if (existing == null) {
                PassportIdentity created = new PassportIdentity();
                created.setSubjectId(subjectId);
                created.setUserId(user.getId());
                created.setWalletAddress(address);
                passportIdentityMapper.insert(created);
            }
        }
        if (Boolean.TRUE.equals(user.getIsBanned())) throw new GlobalException("该社区账号已被禁用，无法登录");
        return user;
    }

    private Map<String, Object> passportClaims(String assertion) {
        String endpoint = requiredPassportConfig(web3Properties.getPassportNodeBaseUrl(), "通行证服务未配置").replaceAll("/+$", "");
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<Map> response = new RestTemplate().exchange(endpoint + "/api/v1/public/auth/passport/assertions/introspect", HttpMethod.POST,
                new HttpEntity<>(Map.of("assertion", assertion), headers), Map.class);
            Map<String, Object> envelope = response.getBody();
            if (envelope == null || !(envelope.get("code") instanceof Number) || ((Number) envelope.get("code")).intValue() != 0) throw new GlobalException("夜莺通行证身份声明无效");
            return asMap(envelope.get("data"));
        } catch (Exception exception) { if (exception instanceof GlobalException) throw (GlobalException) exception; throw new GlobalException("无法校验夜莺通行证身份声明"); }
    }

    private void applyPassportEmail(User user, Map<String, Object> result) {
        Map<String, Object> claims = asMap(result.get("claims")); String email = string(claims.get("email")).toLowerCase();
        if (email.isBlank()) return;
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (existing != null && !existing.getId().equals(user.getId())) throw new GlobalException("该通行证邮箱已关联其他社区账号");
        User update = new User(); update.setId(user.getId()); update.setEmail(email); userMapper.updateById(update);
    }

    @SuppressWarnings("unchecked") private Map<String, Object> asMap(Object value) { return value instanceof Map ? (Map<String, Object>) value : new HashMap<>(); }
    private List<String> stringList(Object value) { return value instanceof List ? ((List<?>) value).stream().map(this::string).toList() : List.of(); }
    private String string(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String requiredPassportConfig(String value, String message) { if (value == null || value.isBlank()) throw new GlobalException(message); return value.trim(); }

    private String buildNonceKey(String address, String chainId) {
        return StrUtil.join(":", RedisKey.IM_AUTH_SIWE_NONCE, chainId, address);
    }

    private String buildChallengeKey(String address) {
        return StrUtil.join(":", RedisKey.IM_AUTH_SIWE_NONCE, "challenge", address);
    }

    private String buildChallenge(String address, String nonce, long issuedAt, String chainId) {
        String domain = web3Properties.getExpectedDomain();
        String header = StrUtil.isBlank(domain) ? "Yeying Social" : domain;
        return header + " wants you to sign in.\n\n"
            + "address: " + address + "\n"
            + "nonce: " + nonce + "\n"
            + "issuedAt: " + issuedAt + "\n"
            + "chainId: " + chainId;
    }

    private String normalizeAddress(String address) {
        return address == null ? "" : address.trim().toLowerCase();
    }

    private String normalizeChainId(String chainId) {
        if (StrUtil.isBlank(chainId)) {
            return web3Properties.getDefaultChainId();
        }
        return chainId.trim();
    }

    private User createUserForWallet(String address, String chainId) {
        String userName = generateUniqueUserName(address);
        String nickName = shortAddress(address);
        User user = new User();
        user.setUserName(userName);
        user.setNickName(nickName);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setType(1);
        user.setSignature("");
        user.setSex(0);
        user.setIsBanned(false);
        user.setWalletAddress(address);
        user.setWalletType(CHAIN_TYPE_EVM);
        user.setWalletVerifiedAt(new Date());
        if (Boolean.TRUE.equals(web3Properties.getDidEnabled())) {
            user.setDid(buildDid(chainId, address));
        }
        userMapper.insert(user);
        return user;
    }

    private void setPrimaryWallet(Long userId, Wallet wallet, String chainId) {
        LambdaUpdateWrapper<Wallet> clearWrapper = Wrappers.lambdaUpdate();
        clearWrapper.eq(Wallet::getUserId, userId).set(Wallet::getIsPrimary, false);
        walletService.update(clearWrapper);
        Wallet updateWallet = new Wallet();
        updateWallet.setId(wallet.getId());
        updateWallet.setIsPrimary(true);
        walletService.updateById(updateWallet);
        User update = new User();
        update.setId(userId);
        update.setWalletAddress(wallet.getAddress());
        update.setWalletType(wallet.getChainType());
        update.setWalletVerifiedAt(new Date());
        if (Boolean.TRUE.equals(web3Properties.getDidEnabled())) {
            update.setDid(buildDid(chainId, wallet.getAddress()));
        }
        userMapper.updateById(update);
    }

    private String generateUniqueUserName(String address) {
        String base = "w_" + address.replace("0x", "").substring(0, 8);
        String candidate = base;
        int attempts = 0;
        while (findUserByUserName(candidate) != null) {
            String suffix = RandomUtil.randomString(4).toLowerCase();
            candidate = base + suffix;
            if (candidate.length() > 20) {
                candidate = candidate.substring(0, 20);
            }
            attempts++;
            if (attempts > 10) {
                base = "w_" + RandomUtil.randomString(10).toLowerCase();
                candidate = base;
            }
        }
        return candidate;
    }

    private String shortAddress(String address) {
        if (address.length() <= 12) {
            return address;
        }
        return address.substring(0, 6) + "..." + address.substring(address.length() - 4);
    }

    private User findUserByUserName(String userName) {
        LambdaQueryWrapper<User> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(User::getUserName, userName);
        return userMapper.selectOne(wrapper);
    }

    private String buildDid(String chainId, String address) {
        return "did:pkh:eip155:" + chainId + ":" + normalizeAddress(address);
    }
}
