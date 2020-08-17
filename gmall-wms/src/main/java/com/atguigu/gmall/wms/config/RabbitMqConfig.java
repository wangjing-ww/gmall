package com.atguigu.gmall.wms.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class RabbitMqConfig {
    //声明延时交换机
    // 声明延时队列
    @Bean
    public Queue ttlQueue(){
        return QueueBuilder.durable("STOCK-TTL-QUEUE")
                .withArgument("x-message-ttl", 90000)
                .withArgument("x-dead-letter-exchange", "ORDER-EXCHANGE")
                .withArgument("x-dead-letter-routing-key", "order.unlock")
                .build();
    }
    // 把延时队列 绑定到 交换机
    @Bean
    public Binding ttlBinding(){
        return new Binding("STOCK-TTL-QUEUE", Binding.DestinationType.QUEUE,
                "ORDER-EXCHANGE","stock.ttl",null);
    }

    // 声明死信交换机：借用order-exchange
    /**
     *   @RabbitListener(bindings = @QueueBinding(
     *             value = @Queue(value = "ORDER-STOCK-QUEUE",durable = "true"),
     *             exchange = @Exchange(value = "ORDER-EXCHANGE",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
     *             key = {"stock.unlock"}
     *     ))
     */
    // 声明死信队列：借用order-stock-queue

    // 把死信队列绑定到死信交换机：注解中已绑定

}
