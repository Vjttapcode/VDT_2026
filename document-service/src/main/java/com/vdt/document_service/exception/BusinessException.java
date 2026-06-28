// exception/BusinessException.java
package com.vdt.document_service.exception;
public class BusinessException extends RuntimeException {
    public BusinessException(String m) { super(m); }
}