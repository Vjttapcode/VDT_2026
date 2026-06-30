package com.vdt.notification_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vdt.notification_service.entity.AlertConfig;

public interface AlertConfigRepository extends JpaRepository<AlertConfig, Long>{
    Optional<AlertConfig> findByDocumentLevel(String level);
    
}
