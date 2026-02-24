package com.antigravity.models;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {

    /**
     * Standard read — used by VerifyCapitalConstraint (READ_COMMITTED, no lock).
     * Safe for high-concurrency read paths from Trade Agent.
     */
    Optional<Portfolio> findByUserId(String userId);

    /**
     * Pessimistic write lock — used by deposit and withdrawal write paths only.
     * Ensures serialized access during capital mutations per architecture design.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Portfolio p WHERE p.userId = :userId")
    Optional<Portfolio> findByUserIdForUpdate(@Param("userId") String userId);
}
