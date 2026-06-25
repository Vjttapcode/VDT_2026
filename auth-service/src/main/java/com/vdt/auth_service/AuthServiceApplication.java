package com.vdt.auth_service;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthServiceApplication {

	public static void main(String[] args) {
		// JVM trên Windows có thể report default TZ là "Asia/Saigon" (tên cũ),
		// Postgres không nhận → ép về tên IANA chuẩn trước khi mở connection.
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
		SpringApplication.run(AuthServiceApplication.class, args);
	}

}
