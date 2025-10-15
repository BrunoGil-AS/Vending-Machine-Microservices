package com.vendingmachine.dispensing.dispensing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DispensingOperationRepository extends JpaRepository<DispensingOperation, Long> {

    List<DispensingOperation> findByTransactionId(Long transactionId);

    Optional<DispensingOperation> findByTransactionIdAndProductId(Long transactionId, Long productId);
}