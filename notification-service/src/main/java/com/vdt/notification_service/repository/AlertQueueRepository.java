package com.vdt.notification_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vdt.notification_service.entity.AlertQueue;

public interface AlertQueueRepository extends JpaRepository<AlertQueue, Long>{
    List<AlertQueue> findTop50ByStatusOrderByCreatedAt(String status);
}
