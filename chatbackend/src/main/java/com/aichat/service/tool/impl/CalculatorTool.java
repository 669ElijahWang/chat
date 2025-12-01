package com.aichat.service.tool.impl;

import com.aichat.service.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.Map;

@Component("calculator")
@Slf4j
public class CalculatorTool implements ToolExecutor {
    
    private final ScriptEngine engine;
    
    public CalculatorTool() {
        this.engine = new ScriptEngineManager().getEngineByName("JavaScript");
    }
    
    @Override
    public Object execute(Map<String, Object> params) {
        String expression = (String) params.get("expression");
        
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        
        try {
            Object result = engine.eval(expression);
            log.info("计算结果: {} = {}", expression, result);
            return Map.of(
                "expression", expression,
                "result", result,
                "success", true
            );
        } catch (Exception e) {
            log.error("计算错误: {}", expression, e);
            return Map.of(
                "expression", expression,
                "error", e.getMessage(),
                "success", false
            );
        }
    }
    
    @Override
    public String getToolName() {
        return "calculator";
    }
}

