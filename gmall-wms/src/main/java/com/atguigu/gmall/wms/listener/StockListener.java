package com.atguigu.gmall.wms.listener;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.wms.mapper.WareSkuMapper;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.rabbitmq.client.Channel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class StockListener {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private WareSkuMapper wareSkuMapper;
    private final  static String KEY_PREFIX = "stock:lock:";

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "ORDER-STOCK-QUEUE",durable = "true"),
            exchange = @Exchange(value = "ORDER-EXCHANGE",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key = {"stock.unlock"}
    ))
    public void listener(String orderToken, Channel channel, Message message) throws IOException {

        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + orderToken);
            if (StringUtils.isNotBlank(json)) {
                List<SkuLockVo> skuLockVos = JSON.parseArray(json, SkuLockVo.class);
                skuLockVos.forEach(skuLockVo -> {
                    wareSkuMapper.unLockStore(skuLockVo.getWareSkuId().longValue(), skuLockVo.getCount());
                });
                // 删除redis中的缓存信息
                redisTemplate.delete(KEY_PREFIX + orderToken);
            }
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            e.printStackTrace();
            if (message.getMessageProperties().getRedelivered()) {
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            } else {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            }
        }
    }

    // 下单成功后减库存
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "STOCK-MINUS-QUEUE",durable = "true"),
            exchange = @Exchange(value = "ORDER-EXCHANGE",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key = {"stock.minus"}
    ))
    public void minusStock(String orderToken, Channel channel, Message message) throws IOException {

        // 从redis中获取锁定得库存
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + orderToken);
            if (StringUtils.isNotBlank(json)){
                List<SkuLockVo> skuLockVos = JSON.parseArray(json, SkuLockVo.class);
                skuLockVos.forEach(skuLockVo -> {
                    wareSkuMapper.minus(skuLockVo.getSkuId(),skuLockVo.getCount());
                });
                //从redis中删除缓存
                redisTemplate.delete(KEY_PREFIX+orderToken);

            }
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), true);
        } catch (IOException e) {

            e.printStackTrace();
            if (message.getMessageProperties().getRedelivered()){
                channel.basicReject(message.getMessageProperties().getDeliveryTag(),false);
            }else {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(),false, true);
            }
        }

    }
}
