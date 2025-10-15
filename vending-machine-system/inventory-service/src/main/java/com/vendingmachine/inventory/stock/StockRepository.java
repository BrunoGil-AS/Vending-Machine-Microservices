package com.vendingmachine.inventory.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByProductId(Long productId);

    @Query("SELECT COUNT(s) FROM Stock s WHERE s.quantity < :threshold")
    long countByQuantityLessThan(int threshold);
}
