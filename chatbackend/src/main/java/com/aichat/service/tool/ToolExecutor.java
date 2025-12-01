package com.aichat.service.tool;

import java.util.Map;

/**
 * 工具执行器接口
 */
public interface ToolExecutor {
    
    /**
     * 执行工具
     * @param params 输入参数
     * @return 执行结果
     */
    Object execute(Map<String, Object> params);
    
    /**
     * 获取工具名称
     */
    String getToolName();
}

