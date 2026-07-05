package com.vdt.document_service.dto;

import java.util.List;
import java.util.Map;

/** Số liệu phân tích toàn hệ thống cho ADMIN. */
public record AdminAnalyticsDto(
        long total,
        Map<String, Long> byStatus,
        Map<String, Long> byType,
        List<DeptStat> byDepartment,
        long[] monthlyExpiry) {      // 12 phần tử — số văn bản hết hạn theo tháng năm hiện tại

    public record DeptStat(Long departmentId, long total, long expiringSoon, long expired) {}
}
