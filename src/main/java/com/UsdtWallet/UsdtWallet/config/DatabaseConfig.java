package com.UsdtWallet.UsdtWallet.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableJpaRepositories(basePackages = "com.UsdtWallet.UsdtWallet.repository")
@EnableTransactionManagement
@RequiredArgsConstructor
@Slf4j
public class DatabaseConfig {

    @Autowired
    private Environment environment;

    @Bean
    @Profile("dev")
    public DataSource devDataSource() {
//        log.info("Configuring development database");
        return DataSourceBuilder.create()
                .url(environment.getProperty("spring.datasource.url"))
                .username(environment.getProperty("spring.datasource.username"))
                .password(environment.getProperty("spring.datasource.password"))
                .driverClassName(environment.getProperty("spring.datasource.driver-class-name"))
                .build();
    }

//    @Bean
//    @Profile("prod")
//    public DataSource prodDataSource() {
////        log.info("Configuring production database");
//        return DataSourceBuilder.create()
//                .url(environment.getProperty("DATABASE_URL"))
//                .username(environment.getProperty("DATABASE_USERNAME"))
//                .password(environment.getProperty("DATABASE_PASSWORD"))
//                .build();
//    }
}
