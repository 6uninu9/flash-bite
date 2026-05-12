package com.smart.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDeduplication implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    
    /**
     * 消息唯一ID（业务生成）
     */
    private String messageId;
    
    /**
     * 消息类型（支持多业务线去重）
     */
    private String messageType;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}