package com.atguigu.gmall.oms.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    //声明延时交换机
    // 声明延时队列
    @Bean
    public Queue ttlQueue(){
       return QueueBuilder.durable("ORDER-TTL-QUEUE")
                .withArgument("x-message-ttl", 60000)
                .withArgument("x-dead-letter-exchange", "ORDER-EXCHANGE")
                .withArgument("x-dead-letter-routing-key", "order.dead")
                .build();
    }
    // 把延时队列 绑定到 交换机
    @Bean
    public Binding ttlBinding(){
        return new Binding("ORDER-TTL-QUEUE", Binding.DestinationType.QUEUE,
                "ORDER-EXCHANGE","order.create",null);
    }
    // 声明死信交换机
    //声明死信队列
    @Bean
    public Queue deadQueue(){
        return new Queue("ORDER-DEAD-QUEUE", true, false, false);
    }
    //把死信交换机 绑定到 死信队列
    @Bean
    public Binding deadBinding(){
        return new Binding("ORDER-DEAD-QUEUE", Binding.DestinationType.QUEUE,
                "ORDER-EXCHANGE","order.dead",null);
    }
}
