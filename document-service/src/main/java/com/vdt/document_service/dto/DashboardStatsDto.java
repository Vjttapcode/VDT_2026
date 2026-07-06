package com.vdt.document_service.dto;

import java.util.List;

public record DashboardStatsDto(
        long active,
        long warning,
        long expired,
        long pending,
        long approved,   // đã duyệt, chờ đến ngày hiệu lực
        List<ExpiringItem> expiringIn30Days
) {
    public record ExpiringItem(Long docId, String title, String level, long daysLeft) {}
}
