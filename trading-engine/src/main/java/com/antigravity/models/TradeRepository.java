package com.antigravity.models;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeRepository extends JpaRepository<Trade, UUID> {
    List<Trade> findByAssetId(String assetId);

    List<Trade> findByUserIdOrderByTimestampDesc(String userId);
}
