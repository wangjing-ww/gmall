package com.atguigu.gmall.payment.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.utils.OrderException;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.payment.entity.PaymentInfoEntity;
import com.atguigu.gmall.payment.feign.GmallOmsClient;
import com.atguigu.gmall.payment.mapper.PaymentInfoMapper;
import com.atguigu.gmall.payment.vo.PayAsyncVo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;

@Service
public class PaymentService {
    @Autowired
    private GmallOmsClient omsClient;

    @Autowired
    private PaymentInfoMapper paymentInfoMapper;

    public OrderEntity queryOrderByOrderToken(String orderToken) {
        ResponseVo<OrderEntity> orderEntityResponseVo = omsClient.queryOrderByOrdersn(orderToken);
        OrderEntity orderEntity = orderEntityResponseVo.getData();
        return orderEntity;
    }

    public Long save(OrderEntity orderEntity, Integer payType) {
        //查看 记录是否已经存在
         PaymentInfoEntity paymentInfoEntity = this.paymentInfoMapper.selectOne(new QueryWrapper<PaymentInfoEntity>().eq("out_trade_no", orderEntity.getOrderSn()));

         // 如果存在 直接返回
        if (paymentInfoEntity == null) {
              return paymentInfoEntity.getId();
        }
        //如果不存在
        paymentInfoEntity = new PaymentInfoEntity();
        paymentInfoEntity.setOutTradeNo(orderEntity.getOrderSn());
        paymentInfoEntity.setSubject("谷粒商城支付服务");
        paymentInfoEntity.setTotalAmount(new BigDecimal("0.01"));
        paymentInfoEntity.setPaymentType(1);
        paymentInfoEntity.setCreateTime(new Date());
        paymentInfoEntity.setPaymentType(payType);
        paymentInfoEntity.setPaymentStatus(0);

        paymentInfoMapper.insert(paymentInfoEntity);
        return paymentInfoEntity.getId();


    }

    public PaymentInfoEntity queryPaymentById(Long id) {
        return paymentInfoMapper.selectById(id);
    }



    public void paySuccess(PayAsyncVo payAsyncVo) {
        PaymentInfoEntity paymentInfoEntity = new PaymentInfoEntity();
        paymentInfoEntity.setCreateTime(new Date());
        paymentInfoEntity.setPaymentStatus(1);
        paymentInfoEntity.setCallbackContent(JSON.toJSONString(payAsyncVo));
        paymentInfoMapper.update(paymentInfoEntity, new UpdateWrapper<PaymentInfoEntity>().eq("out_trade_no", payAsyncVo.getOut_trade_no()));
    }
}
