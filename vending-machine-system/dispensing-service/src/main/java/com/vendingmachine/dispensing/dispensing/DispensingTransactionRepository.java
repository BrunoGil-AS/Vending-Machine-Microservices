package com.vendingmachine.dispensing.dispensing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DispensingTransactionRepository extends JpaRepository<DispensingTransaction, Long> {

    List<DispensingTransaction> findByTransactionId(Long transactionId);

    Optional<DispensingTransaction> findByTransactionIdAndProductId(Long transactionId, Long productId);
}