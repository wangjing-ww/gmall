package com.atguigu.gmall.oms.service.impl;

import com.atguigu.gmall.oms.entity.OrderItemEntity;
import com.atguigu.gmall.oms.entity.OrderItemVo;
import com.atguigu.gmall.oms.entity.OrderSubmitVo;
import com.atguigu.gmall.oms.mapper.OrderItemMapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.oms.mapper.OrderMapper;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.oms.service.OrderService;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderEntity> implements OrderService {

    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<OrderEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<OrderEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public OrderEntity saveOrder(OrderSubmitVo orderSubmitVo, Long userId) {
        OrderEntity orderEntity = new OrderEntity();
        //保存订单
        BeanUtils.copyProperties(orderSubmitVo, orderEntity);
        orderEntity.setOrderSn(orderSubmitVo.getOrderToken());
        orderEntity.setUserId(userId);
        orderEntity.setCreateTime(new Date());
        orderEntity.setTotalAmount(orderSubmitVo.getTotalPrice());
        orderEntity.setPayType(orderSubmitVo.getPayType());
        orderEntity.setDeliveryCompany(orderSubmitVo.getDeliveryCompany());
        this.save(orderEntity);
        //保存订单详情
        List<OrderItemVo> orderItems = orderSubmitVo.getItems();
        orderItems.forEach(orderItemVo -> {
            OrderItemEntity orderItemEntity = new OrderItemEntity();
            orderItemEntity.setOrderId(orderEntity.getId());
            orderItemEntity.setOrderSn(orderEntity.getOrderSn());
            // 需要远程查询spu信息 TODO
            // 设置sku信息
            orderItemEntity.setSkuId(orderItemVo.getSkuId());
            orderItemEntity.setSkuName(orderItemVo.getTitle());
            orderItemEntity.setSkuPrice(orderItemVo.getPrice());
            orderItemEntity.setSkuQuantity(orderItemVo.getCount());
            //需要远程查询优惠信息 TODO
            // 优惠信息
            this.orderItemMapper.insert(orderItemEntity);
        });
        rabbitTemplate.convertAndSend("order-exchange", "order.create", orderSubmitVo.getOrderToken());
        return orderEntity;
    }

}