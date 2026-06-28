// exception/ForbiddenException.java  (Ngày 5 self-approval cần)
package com.vdt.document_service.exception;
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String m) { super(m); }
    public ForbiddenException() { super("Không có quyền thực hiện hành động này"); }
}