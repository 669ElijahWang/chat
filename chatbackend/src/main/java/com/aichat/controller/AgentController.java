package com.aichat.controller;

import com.aichat.domain.dto.common.ApiResponse;
import com.aichat.domain.entity.Agent;
import com.aichat.domain.entity.Tool;
import com.aichat.security.UserPrincipal;
import com.aichat.service.AgentService;
import com.aichat.service.tool.ToolService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/agents")
@RequiredArgsConstructor
public class AgentController {
    
    private final AgentService agentService;
    private final ToolService toolService;
    
    /**
     * 创建Agent
     */
    @PostMapping
    public ApiResponse<Agent> createAgent(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CreateAgentRequest request) {
        Agent agent = agentService.createAgent(
                userPrincipal.getId(),
                request.getName(),
                request.getDescription(),
                request.getSystemPrompt(),
                request.getModel(),
                request.getTemperature(),
                request.getMaxTokens(),
                request.getTools(),
                request.getKnowledgeBaseIds()
        );
        return ApiResponse.success("创建成功", agent);
    }
    
    /**
     * 获取用户的Agent列表
     */
    @GetMapping
    public ApiResponse<Page<Agent>> getAgents(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Agent> agents = agentService.getUserAgents(userPrincipal.getId(), pageable);
        return ApiResponse.success(agents);
    }
    
    /**
     * 获取Agent详情
     */
    @GetMapping("/{id}")
    public ApiResponse<Agent> getAgent(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id) {
        Agent agent = agentService.getAgent(id, userPrincipal.getId());
        return ApiResponse.success(agent);
    }
    
    /**
     * 更新Agent
     */
    @PutMapping("/{id}")
    public ApiResponse<Agent> updateAgent(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateAgentRequest request) {
        Agent agent = agentService.updateAgent(
                id,
                userPrincipal.getId(),
                request.getName(),
                request.getDescription(),
                request.getSystemPrompt(),
                request.getModel(),
                request.getTemperature(),
                request.getMaxTokens(),
                request.getTools(),
                request.getKnowledgeBaseIds()
        );
        return ApiResponse.success("更新成功", agent);
    }
    
    /**
     * 删除Agent
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAgent(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id) {
        agentService.deleteAgent(id, userPrincipal.getId());
        return ApiResponse.success("删除成功", null);
    }
    
    /**
     * 获取所有可用工具
     */
    @GetMapping("/tools")
    public ApiResponse<List<Tool>> getAvailableTools() {
        List<Tool> tools = toolService.getAllActiveTools();
        return ApiResponse.success(tools);
    }
    
    /**
     * 按分类获取工具
     */
    @GetMapping("/tools/category/{category}")
    public ApiResponse<List<Tool>> getToolsByCategory(@PathVariable String category) {
        List<Tool> tools = toolService.getToolsByCategory(category);
        return ApiResponse.success(tools);
    }
    
    @Data
    static class CreateAgentRequest {
        @NotBlank(message = "Agent名称不能为空")
        private String name;
        
        private String description;
        
        private String systemPrompt;
        
        private String model;
        
        private BigDecimal temperature;
        
        private Integer maxTokens;
        
        private Map<String, Object> tools;
        
        private Long[] knowledgeBaseIds;
    }
    
    @Data
    static class UpdateAgentRequest {
        private String name;
        private String description;
        private String systemPrompt;
        private String model;
        private BigDecimal temperature;
        private Integer maxTokens;
        private Map<String, Object> tools;
        private Long[] knowledgeBaseIds;
    }
}

