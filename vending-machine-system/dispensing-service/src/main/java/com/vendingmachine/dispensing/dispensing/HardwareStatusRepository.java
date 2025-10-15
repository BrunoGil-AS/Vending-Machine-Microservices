package com.vendingmachine.dispensing.dispensing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HardwareStatusRepository extends JpaRepository<HardwareStatus, Long> {

    Optional<HardwareStatus> findByComponentName(String componentName);

    List<HardwareStatus> findByStatus(String status);
}