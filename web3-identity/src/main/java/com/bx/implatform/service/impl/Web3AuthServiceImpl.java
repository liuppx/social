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
import com.bx.implatform.entity.Wallet;
import com.bx.implatform.exception.GlobalException;
import com.bx.implatform.mapper.UserMapper;
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

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
        redisTemplate.opsForValue().set(key, challenge, web3Properties.getNonceExpireIn(), TimeUnit.SECONDS);
        Web3ChallengeVO vo = new Web3ChallengeVO();
        vo.setChallenge(challenge);
        vo.setNonce(nonce);
        vo.setIssuedAt(issuedAt);
        vo.setExpiresAt(expiresAt);
        vo.setChainId(chainId);
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
        if (storedChallenge == null) {
            throw new GlobalException("挑战已过期，请重新获取");
        }
        String challenge = storedChallenge.toString();
        if (!Web3SignatureVerifier.verifyPersonalSign(challenge, dto.getSignature(), address)) {
            throw new GlobalException("签名校验失败");
        }
        redisTemplate.delete(key);
        User user = resolveUserByWallet(address, chainId);
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
