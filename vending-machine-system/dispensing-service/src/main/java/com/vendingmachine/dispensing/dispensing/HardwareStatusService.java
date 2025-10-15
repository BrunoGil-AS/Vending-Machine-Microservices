package com.vendingmachine.dispensing.dispensing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HardwareStatusService {

    private final HardwareStatusRepository hardwareStatusRepository;

    @Transactional
    public void initializeHardwareComponents() {
        String[] components = {"dispenser_motor", "product_chute", "sensor_array", "control_board"};

        for (String component : components) {
            if (hardwareStatusRepository.findByComponentName(component).isEmpty()) {
                HardwareStatus status = new HardwareStatus();
                status.setComponentName(component);
                status.setStatus("OPERATIONAL");
                status.setLastChecked(LocalDateTime.now());
                hardwareStatusRepository.save(status);
                log.info("Initialized hardware component: {}", component);
            }
        }
    }

    @Transactional
    public void reportHardwareError(String componentName, String error) {
        Optional<HardwareStatus> optionalStatus = hardwareStatusRepository.findByComponentName(componentName);
        if (optionalStatus.isPresent()) {
            HardwareStatus status = optionalStatus.get();
            status.setStatus("OUT_OF_ORDER");
            status.setLastError(error);
            status.setLastChecked(LocalDateTime.now());
            hardwareStatusRepository.save(status);
            log.warn("Hardware error reported for {}: {}", componentName, error);
        } else {
            log.error("Unknown hardware component: {}", componentName);
        }
    }

    @Transactional
    public void markComponentOperational(String componentName) {
        Optional<HardwareStatus> optionalStatus = hardwareStatusRepository.findByComponentName(componentName);
        if (optionalStatus.isPresent()) {
            HardwareStatus status = optionalStatus.get();
            status.setStatus("OPERATIONAL");
            status.setLastError(null);
            status.setLastChecked(LocalDateTime.now());
            hardwareStatusRepository.save(status);
            log.info("Marked hardware component {} as operational", componentName);
        }
    }

    public List<HardwareStatus> getAllHardwareStatus() {
        return hardwareStatusRepository.findAll();
    }

    public boolean isHardwareOperational() {
        List<HardwareStatus> statuses = hardwareStatusRepository.findByStatus("OUT_OF_ORDER");
        return statuses.isEmpty();
    }
}