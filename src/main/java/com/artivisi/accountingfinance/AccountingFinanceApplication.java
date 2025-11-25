package com.artivisi.accountingfinance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AccountingFinanceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccountingFinanceApplication.class, args);
	}

}
