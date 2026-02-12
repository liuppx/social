package com.bx.implatform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bx.implatform.entity.Wallet;

import java.util.List;

public interface WalletService extends IService<Wallet> {

    Wallet findByAddress(String address, String chainType);

    List<Wallet> findByUserId(Long userId);
}
