package com.atguigu.gmall.oms.listener;

import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.oms.entity.UserBoundVO;
import com.atguigu.gmall.oms.mapper.OrderMapper;
import com.atguigu.gmall.oms.service.OrderService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OrderListener {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OrderService orderService;
    @RabbitListener(queues = {"ORDER-DEAD-QUEUE"})
    public void closeOrder(String orderToken, Channel channel, Message message) throws IOException {
        try {
            if (orderMapper.closeOrder(orderToken)==1){
                rabbitTemplate.convertAndSend("ORDER-EXCHANGE", "order.unlock", orderToken);
            }
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            e.printStackTrace();
            if (message.getMessageProperties().getRedelivered()){
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            } else {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            }
        }

    }
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "ORDER-PAY-QUEUE",durable = "true"),
            exchange = @Exchange(value = "ORDER-EXCHANGE",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key = {"order.pay"}
    ))
    public void successOrder(String orderToken,Channel channel,Message message) throws IOException {
        if(orderMapper.successOrder(orderToken) ==1){
            //如果订单支付成功 真正得减库存
            rabbitTemplate.convertAndSend("ORDER-EXCHANGE", "stock.minus", orderToken);

            //给用户添加积分信息
            OrderEntity orderEntity = orderService.getOne(new QueryWrapper<OrderEntity>().eq("order_sn", orderToken));
            UserBoundVO userBoundVO = new UserBoundVO();
            userBoundVO.setUserId(orderEntity.getUserId());
            userBoundVO.setGrowth(orderEntity.getGrowth());
            userBoundVO.setIntegration(orderEntity.getIntegration());
            rabbitTemplate.convertAndSend("ORDER-EXCHANGE", "bound.plus", userBoundVO);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        }
    }
}
