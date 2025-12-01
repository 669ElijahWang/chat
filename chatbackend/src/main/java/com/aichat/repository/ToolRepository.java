package com.aichat.repository;

import com.aichat.domain.entity.Tool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ToolRepository extends JpaRepository<Tool, Long> {
    
    Optional<Tool> findByName(String name);
    
    List<Tool> findByStatus(Tool.Status status);
    
    List<Tool> findByCategory(String category);
    
    List<Tool> findByStatusAndCategory(Tool.Status status, String category);
}

