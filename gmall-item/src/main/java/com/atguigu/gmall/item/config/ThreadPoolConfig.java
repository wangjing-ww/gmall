package com.atguigu.gmall.item.config;

import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {
    @Bean
    public ThreadPoolExecutor threadPoolExecutor(){
       return new ThreadPoolExecutor(100, 1000, 60, TimeUnit.SECONDS,new ArrayBlockingQueue<>(10000));
    }
}