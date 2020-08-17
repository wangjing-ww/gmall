package com.atguigu.gmall.payment.controller;

import com.alipay.api.AlipayApiException;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.bean.UserInfo;
import com.atguigu.gmall.common.utils.OrderException;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.payment.config.AlipayTemplate;
import com.atguigu.gmall.payment.entity.PaymentInfoEntity;
import com.atguigu.gmall.payment.interceptor.LoginInterceptor;
import com.atguigu.gmall.payment.service.PaymentService;
import com.atguigu.gmall.payment.vo.PayAsyncVo;
import com.atguigu.gmall.payment.vo.PayVo;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sun.xml.internal.bind.v2.model.core.ID;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Controller
public class PaymentController {
    @Autowired
    private PaymentService paymentService;

    @Autowired
    private  StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    private AlipayTemplate alipayTemplate;
    @GetMapping("pay.html")
    public String toPay(@RequestParam("orderToken")String orderToken, Model model){
       OrderEntity orderEntity = paymentService.queryOrderByOrderToken(orderToken);
        model.addAttribute("orderEntity", orderEntity);
        return "pay";
    }
    @GetMapping("alipay.html")
    public String alipay(@RequestParam("orderToken")String orderToken){
        try {
            OrderEntity orderEntity = paymentService.queryOrderByOrderToken(orderToken);
            if (orderEntity == null) {
                throw new OrderException("此订单无法支付 请重新下单");
            }

            //
            PayVo payVo = new PayVo();
            payVo.setOut_trade_no(orderToken);
            payVo.setTotal_amount("0.01");
            payVo.setSubject("谷粒商城支付服务");
            Long payId  = paymentService.save(orderEntity,1);
            payVo.setPassback_params(payId.toString());

            String result = alipayTemplate.pay(payVo);
            return result;
        } catch (AlipayApiException e) {
            e.printStackTrace();
            throw new OrderException("支付出错 请刷新重试");
        }
    }

    @PostMapping("pay/success")
    @ResponseBody
    public String paySuccess(PayAsyncVo payAsyncVo)  {
        //验签
        Boolean flag =  alipayTemplate.verifySignature(payAsyncVo);
        if (!flag){
            return "failure";
        }
        // 2验签成功后 按照支付结果异步通知中的描述 对支付结果中的业务内容进行二次校验
        String payId = payAsyncVo.getPassback_params();
        if (StringUtils.isBlank(payId)){
            return "failure";
        }
        PaymentInfoEntity paymentInfoEntity = paymentService.queryPaymentById(Long.valueOf(payId));
         if (paymentInfoEntity==null
                 || !StringUtils.equals(payAsyncVo.getApp_id(), alipayTemplate.getApp_id())
                 || !StringUtils.equals(payAsyncVo.getOut_trade_no(), paymentInfoEntity.getOutTradeNo())
                 || paymentInfoEntity.getTotalAmount().compareTo(new BigDecimal(payAsyncVo.getBuyer_pay_amount())) != 0
          ){
             return "failure";
         }

         //3 校验支付状态 根据trade_status进行后续的业务处理
        if (!StringUtils.equals("TRADE_SUCCESS", payAsyncVo.getTrade_status())){
            return "failure";
        }
        //4 正常支付成功 记录支付记录方便到账
        paymentService.paySuccess(payAsyncVo);
        //5 发送消息 更新订单状态 并减库存
        rabbitTemplate.convertAndSend("ORDER-EXCHANGE", "order.pay", payAsyncVo.getOut_trade_no());
        // 6 给支付宝回执

        return "success";
    }
    // 查询订单数据展示在支付成功页面
    @GetMapping("pay/ok")
    public String payOk(PayAsyncVo payAsyncVo){
        // 查询订单数据展示在支付成功页面
        // String orderToken = payAsyncVo.getOut_trade_no();
        // TODO：查询并通过model响应给页面
        return "paysuccess";
    }

    @GetMapping("/miaosha/{skuId}")
    public ResponseVo<Object> kill(@PathVariable("skuId")Long skuId){
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();
        if (userId != null) {
            //查询库存
            String stock = redisTemplate.opsForValue().get("sec:stock:" + skuId);
            if (StringUtils.isEmpty(stock)){
                return ResponseVo.ok("秒杀结束");
            }
            //通过信号量 获取秒杀库存
            RSemaphore semaphore = redissonClient.getSemaphore("sec:semaphore:" + stock);
            semaphore.trySetPermits(Integer.valueOf(stock));
            boolean flag = semaphore.tryAcquire();
            if (flag){
                String timeId = IdWorker.getTimeId();
                SkuLockVo skuLockVo = new SkuLockVo();
                skuLockVo.setOrderToken(timeId);
                skuLockVo.setCount(1);
                skuLockVo.setSkuId(skuId);
                // 准备 闭锁信息

                //准备闭锁信息
                RCountDownLatch latch = this.redissonClient.getCountDownLatch("sec:countdown:" + timeId);
                latch.trySetCount(1);

                this.rabbitTemplate.convertAndSend("ORDER-EXCHANGE", "sec.kill",skuLockVo);
                return ResponseVo.ok("秒杀成功，订单号：" + timeId);
            }else {
                return ResponseVo.fail("秒杀失败，欢迎再次秒杀！");
            }
        }
        return ResponseVo.fail("请登录后再试！");
    }
    @GetMapping("/miaosha/pay")
    public String payKillOrder(String orderSn) throws InterruptedException {

        RCountDownLatch latch = this.redissonClient.getCountDownLatch("sec:countdown:" + orderSn);

        latch.await();

        // 查询订单信息

        return "";
    }

}
