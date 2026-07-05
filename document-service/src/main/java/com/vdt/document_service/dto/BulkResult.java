package com.vdt.document_service.dto;

import java.util.List;

/** Kết quả thao tác hàng loạt: số thành công/thất bại + chi tiết lỗi từng văn bản. */
public record BulkResult(int ok, int failed, List<String> errors) {}
