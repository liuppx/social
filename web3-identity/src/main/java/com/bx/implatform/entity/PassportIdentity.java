package com.bx.implatform.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_passport_identity")
public class PassportIdentity {
    @TableId
    private Long id;
    private String subjectId;
    private Long userId;
    private String walletAddress;
}
