package com.admin.service.impl;

import com.admin.entity.CleanupTask;
import com.admin.mapper.CleanupTaskMapper;
import com.admin.service.CleanupTaskService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 待清理任务服务实现
 *
 * @author QAQ
 */
@Service
public class CleanupTaskServiceImpl extends ServiceImpl<CleanupTaskMapper, CleanupTask> implements CleanupTaskService {
}
