package com.rajaev.job;

import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;
import com.rajaev.job.config.EnableElasticJobAnnotation;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableElasticJobAnnotation
@EnableTransactionManagement
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.rajaev.iccard.feign",
		"com.rajaev.fegin.service","com.rajaev.job.service"})
@EnableApolloConfig()
public class RajaevJobApplication {

	public static void main(String[] args) {
		SpringApplication.run(RajaevJobApplication.class, args);
		/*new SpringApplicationBuilder().sources(RajaevJobApplication.class).web(false).run(args);
		try {
			new CountDownLatch(1).await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}*/
	}
}
