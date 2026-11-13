package com.admin.common.dto;

import lombok.Data;

@Data
public class GostDto {
    private Integer code;

    /**
     * 节点回包里的 success 字段。老版本节点不返回该字段，此时为 null，
     * 调用方需要回落到 msg 判断。
     */
    private Boolean success;

    private String msg;
    
    private Object data;  // 添加数据字段，用于存储响应的详细数据
}
