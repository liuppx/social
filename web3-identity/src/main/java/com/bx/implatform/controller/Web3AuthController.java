package com.bx.implatform.controller;

import com.bx.implatform.dto.SiweNonceDTO;
import com.bx.implatform.dto.SiweVerifyDTO;
import com.bx.implatform.dto.WalletLinkDTO;
import com.bx.implatform.dto.WalletUnlinkDTO;
import com.bx.implatform.result.Result;
import com.bx.implatform.result.ResultUtils;
import com.bx.implatform.service.Web3AuthService;
import com.bx.implatform.session.SessionContext;
import com.bx.implatform.vo.LoginVO;
import com.bx.implatform.vo.SiweNonceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Web3认证")
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class Web3AuthController {

    private final Web3AuthService web3AuthService;

    @PostMapping("/siwe/nonce")
    @Operation(summary = "获取SIWE nonce", description = "获取钱包登录nonce")
    public Result<SiweNonceVO> nonce(@Valid @RequestBody SiweNonceDTO dto) {
        return ResultUtils.success(web3AuthService.issueNonce(dto));
    }

    @PostMapping("/siwe/verify")
    @Operation(summary = "SIWE登录", description = "校验签名并登录")
    public Result<LoginVO> verify(@Valid @RequestBody SiweVerifyDTO dto) {
        return ResultUtils.success(web3AuthService.verifyAndLogin(dto));
    }

    @PostMapping("/wallet/link")
    @Operation(summary = "绑定钱包", description = "绑定钱包地址")
    public Result<Void> link(@Valid @RequestBody WalletLinkDTO dto) {
        web3AuthService.linkWallet(SessionContext.getSession(), dto);
        return ResultUtils.success();
    }

    @PostMapping("/wallet/unlink")
    @Operation(summary = "解绑钱包", description = "解绑钱包地址")
    public Result<Void> unlink(@Valid @RequestBody WalletUnlinkDTO dto) {
        web3AuthService.unlinkWallet(SessionContext.getSession(), dto);
        return ResultUtils.success();
    }
}
