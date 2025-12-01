package com.aichat.service.tool;

import com.aichat.domain.entity.Tool;
import com.aichat.exception.BusinessException;
import com.aichat.repository.ToolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ToolService {
    
    private final ToolRepository toolRepository;
    private final Map<String, ToolExecutor> toolExecutors;
    
    @Transactional(readOnly = true)
    public List<Tool> getAllActiveTools() {
        return toolRepository.findByStatus(Tool.Status.ACTIVE);
    }
    
    @Transactional(readOnly = true)
    public Tool getTool(Long id) {
        return toolRepository.findById(id)
                .orElseThrow(() -> new BusinessException("工具不存在"));
    }
    
    @Transactional(readOnly = true)
    public Tool getToolByName(String name) {
        return toolRepository.findByName(name)
                .orElseThrow(() -> new BusinessException("工具不存在: " + name));
    }
    
    @Transactional(readOnly = true)
    public List<Tool> getToolsByCategory(String category) {
        return toolRepository.findByStatusAndCategory(Tool.Status.ACTIVE, category);
    }
    
    /**
     * 执行工具
     */
    public Object executeTool(String toolName, Map<String, Object> params) {
        Tool tool = getToolByName(toolName);
        
        if (tool.getStatus() != Tool.Status.ACTIVE) {
            throw new BusinessException("工具已禁用: " + toolName);
        }
        
        ToolExecutor executor = toolExecutors.get(toolName);
        if (executor == null) {
            throw new BusinessException("工具执行器未找到: " + toolName);
        }
        
        log.info("执行工具: {}, params: {}", toolName, params);
        
        try {
            Object result = executor.execute(params);
            log.info("工具执行成功: {}", toolName);
            return result;
        } catch (Exception e) {
            log.error("工具执行失败: {}", toolName, e);
            throw new BusinessException("工具执行失败: " + e.getMessage());
        }
    }
}

