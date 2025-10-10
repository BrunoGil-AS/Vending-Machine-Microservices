package com.vendingmachine.transaction.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // No customer-based queries since transactions are anonymous
    List<Transaction> findAllByOrderByCreatedAtDesc();
}