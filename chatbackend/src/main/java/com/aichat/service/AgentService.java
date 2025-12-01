package com.aichat.service;

import com.aichat.domain.entity.Agent;
import com.aichat.exception.BusinessException;
import com.aichat.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {
    
    private final AgentRepository agentRepository;
    
    @Transactional
    public Agent createAgent(Long userId, String name, String description, String systemPrompt,
                            String model, BigDecimal temperature, Integer maxTokens,
                            Map<String, Object> tools, Long[] knowledgeBaseIds) {
        Agent agent = Agent.builder()
                .userId(userId)
                .name(name)
                .description(description)
                .systemPrompt(systemPrompt)
                .model(model != null ? model : "deepseek-chat")
                .temperature(temperature != null ? temperature : BigDecimal.valueOf(0.7))
                .maxTokens(maxTokens != null ? maxTokens : 2000)
                .tools(tools)
                .knowledgeBaseIds(knowledgeBaseIds)
                .status(Agent.Status.ACTIVE)
                .build();
        
        agent = agentRepository.save(agent);
        log.info("创建Agent: id={}, userId={}, name={}", agent.getId(), userId, name);
        
        return agent;
    }
    
    @Transactional(readOnly = true)
    public Page<Agent> getUserAgents(Long userId, Pageable pageable) {
        return agentRepository.findByUserId(userId, pageable);
    }
    
    @Transactional(readOnly = true)
    public Agent getAgent(Long id, Long userId) {
        return agentRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException("Agent不存在或无权访问"));
    }
    
    @Transactional
    public Agent updateAgent(Long id, Long userId, String name, String description,
                            String systemPrompt, String model, BigDecimal temperature,
                            Integer maxTokens, Map<String, Object> tools, Long[] knowledgeBaseIds) {
        Agent agent = getAgent(id, userId);
        
        if (name != null) agent.setName(name);
        if (description != null) agent.setDescription(description);
        if (systemPrompt != null) agent.setSystemPrompt(systemPrompt);
        if (model != null) agent.setModel(model);
        if (temperature != null) agent.setTemperature(temperature);
        if (maxTokens != null) agent.setMaxTokens(maxTokens);
        if (tools != null) agent.setTools(tools);
        if (knowledgeBaseIds != null) agent.setKnowledgeBaseIds(knowledgeBaseIds);
        
        agent = agentRepository.save(agent);
        log.info("更新Agent: id={}, userId={}", id, userId);
        
        return agent;
    }
    
    @Transactional
    public void deleteAgent(Long id, Long userId) {
        Agent agent = getAgent(id, userId);
        agentRepository.delete(agent);
        log.info("删除Agent: id={}, userId={}", id, userId);
    }
}

