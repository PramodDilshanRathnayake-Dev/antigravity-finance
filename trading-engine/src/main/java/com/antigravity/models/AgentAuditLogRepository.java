package com.antigravity.models;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentAuditLogRepository extends JpaRepository<AgentAuditLog, UUID> {
    List<AgentAuditLog> findByAgentNameOrderByTimestampDesc(String agentName);
}
