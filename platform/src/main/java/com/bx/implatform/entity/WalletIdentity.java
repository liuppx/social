package com.bx.implatform.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("t_wallet_identity")
public class WalletIdentity {
    @TableId
    private Long id;
    private String walletIdentityDid;
    private Long userId;
    private String walletAddress;
    private Date createdTime;
    private Date updatedTime;
}
