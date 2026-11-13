package com.admin.common.utils;

import com.admin.common.dto.GostDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GostUtilTest {

    @Test
    void successFieldWinsOverMessage() {
        GostDto dto = new GostDto();
        dto.setSuccess(true);
        dto.setMsg("节点不在线");
        assertTrue(GostUtil.isSuccess(dto), "success=true 时不应被 msg 覆盖");

        GostDto failed = new GostDto();
        failed.setSuccess(false);
        failed.setMsg("OK");
        assertFalse(GostUtil.isSuccess(failed), "success=false 时不应被 msg=OK 误判成功");
    }

    @Test
    void fallsBackToMessageForOldNodes() {
        GostDto old = new GostDto();
        old.setMsg("OK");
        assertTrue(GostUtil.isSuccess(old), "老节点无 success 字段时应回落比对 msg");

        GostDto oldErr = new GostDto();
        oldErr.setMsg("service xxx not found");
        assertFalse(GostUtil.isSuccess(oldErr));

        assertFalse(GostUtil.isSuccess(null));
    }
}
