package com.bx.implatform.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 钱包绑定
 *
 * @author blue
 */
@Data
@TableName("t_wallet")
public class Wallet {

    /**
     * id
     */
    @TableId
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 钱包地址
     */
    private String address;

    /**
     * 链类型
     */
    private String chainType;

    /**
     * 公钥
     */
    private String publicKey;

    /**
     * 是否主钱包
     */
    private Boolean isPrimary;

    /**
     * 创建时间
     */
    private Date createdTime;
}
