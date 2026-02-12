package com.bx.implatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bx.implatform.entity.Wallet;
import com.bx.implatform.mapper.WalletMapper;
import com.bx.implatform.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl extends ServiceImpl<WalletMapper, Wallet> implements WalletService {

    @Override
    public Wallet findByAddress(String address, String chainType) {
        LambdaQueryWrapper<Wallet> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(Wallet::getAddress, address);
        wrapper.eq(Wallet::getChainType, chainType);
        return this.getOne(wrapper);
    }

    @Override
    public List<Wallet> findByUserId(Long userId) {
        LambdaQueryWrapper<Wallet> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(Wallet::getUserId, userId);
        return this.list(wrapper);
    }
}
