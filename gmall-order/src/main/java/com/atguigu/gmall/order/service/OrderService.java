package com.atguigu.gmall.order.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.bean.UserInfo;

import com.atguigu.gmall.common.utils.OrderException;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.oms.entity.OrderItemVo;
import com.atguigu.gmall.oms.entity.OrderSubmitVo;
import com.atguigu.gmall.order.feign.*;
import com.atguigu.gmall.order.interceptor.LoginInterceptor;
import com.atguigu.gmall.order.vo.OrderConfirmVo;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;

import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import com.atguigu.gmall.ums.entity.UserEntity;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Service
public class OrderService {
    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallUmsClient umsClient;
    @Autowired
    private GmallCartClient cartClient;
    @Autowired
    private GmallSmsClient smsClient;
    @Autowired
    private GmallWmsClient wmsClient;
    @Autowired
    private GmallOmsClient omsClient;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    private static final String KEY_PREFIX = "order:token:";
    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    public OrderConfirmVo confirm() {
        OrderConfirmVo orderConfirmVo = new OrderConfirmVo();
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();
        CompletableFuture<Void> addressCompletableFuture = CompletableFuture.runAsync(() -> {
            // 设置收货地址
            ResponseVo<List<UserAddressEntity>> userAddressRespVo = umsClient.queryUserAddressByUserId(userId);
            List<UserAddressEntity> userAddressEntities = userAddressRespVo.getData();
            orderConfirmVo.setAddresses(userAddressEntities);
        }, threadPoolExecutor);

        CompletableFuture<Void> boundsCompletableFuture = CompletableFuture.runAsync(() -> {
            //查询用户的积分信息
            ResponseVo<UserEntity> userEntityResponseVo = umsClient.queryUserById(userId);
            UserEntity userEntity = userEntityResponseVo.getData();
            if (userEntity != null) {
                Integer integration = userEntity.getIntegration();
                orderConfirmVo.setBounds(integration);
            }
        }, threadPoolExecutor);

        CompletableFuture<Void> tokenCompletableFuture = CompletableFuture.runAsync(() -> {
            //防重的唯一标识
            //long id = IdWorker.getId();
            String timeId = IdWorker.getTimeId();
            redisTemplate.opsForValue().set(KEY_PREFIX + timeId, timeId);
            orderConfirmVo.setOrderToken(timeId);
        }, threadPoolExecutor);

        CompletableFuture<List<Cart>> cartCompletableFuture =  CompletableFuture.supplyAsync(()->{
            //查询送货清单
            ResponseVo<List<Cart>> checkedCartsRespVo = cartClient.queryCheckedCarts(userId);
            List<Cart> carts = checkedCartsRespVo.getData();
            if (CollectionUtils.isEmpty(carts)){
                throw new RuntimeException("没有选择的商品");
            }
            return carts;
         }, threadPoolExecutor);
         cartCompletableFuture.thenAcceptAsync(carts -> {
             List<OrderItemVo> orderItemVos = carts.stream().map(cart -> {
                 OrderItemVo orderItemVo = new OrderItemVo();

                 orderItemVo.setSkuId(cart.getSkuId());
                 orderItemVo.setCount(cart.getCount().intValue());

                 CompletableFuture<Void> skuEntityCompletableFuture = CompletableFuture.runAsync(() -> {
                     // 设置sku属性
                     ResponseVo<SkuEntity> skuEntityResponseVo = pmsClient.querySkuById(cart.getSkuId());
                     SkuEntity skuEntity = skuEntityResponseVo.getData();
                     orderItemVo.setDefaultImage(skuEntity.getDefaultImage());
                     orderItemVo.setWeight(new BigDecimal(skuEntity.getWeight()));
                     orderItemVo.setTitle(skuEntity.getTitle());
                     orderItemVo.setPrice(skuEntity.getPrice());
                 }, threadPoolExecutor);

                 CompletableFuture<Void> skuAttrValueVompletableFuture = CompletableFuture.runAsync(() -> {
                     // 销售属性
                     ResponseVo<List<SkuAttrValueEntity>> skuAttrValueResponseVo = pmsClient.querySearchAttrValueBySkuId(cart.getSkuId());
                     List<SkuAttrValueEntity> skuAttrValueEntities = skuAttrValueResponseVo.getData();
                     if (!CollectionUtils.isEmpty(skuAttrValueEntities)) {
                         orderItemVo.setSaleAttrs(skuAttrValueEntities);
                     }
                 }, threadPoolExecutor);
                 CompletableFuture<Void> itemSaleVosCompletableFuture = CompletableFuture.runAsync(() -> {

                     //营销属性
                     ResponseVo<List<ItemSaleVo>> itemSaleResponseVo = smsClient.querySalesBySkuId(cart.getSkuId());
                     List<ItemSaleVo> itemSaleVos = itemSaleResponseVo.getData();
                  //   orderItemVo.setSaleAttrs(itemSaleVos);
                     orderItemVo.setSales(itemSaleVos);
                 }, threadPoolExecutor);

                 CompletableFuture<Void>  wareSkuCompletableFuture = CompletableFuture.runAsync(() -> {
                     // 查询库存
                     ResponseVo<List<WareSkuEntity>> wareSkuBySkuId = wmsClient.queryWareSkuBySkuId(cart.getSkuId());
                     List<WareSkuEntity> wareSkuEntities = wareSkuBySkuId.getData();
                     if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                         orderItemVo.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
                     }

                 }, threadPoolExecutor);

                 return orderItemVo;
             }).collect(Collectors.toList());
             orderConfirmVo.setItems(orderItemVos);

         }, threadPoolExecutor);

       CompletableFuture.allOf(addressCompletableFuture,boundsCompletableFuture,tokenCompletableFuture,cartCompletableFuture).join();


        return orderConfirmVo;
    }

    public OrderEntity submit(OrderSubmitVo submitVo) {
        // 防止重复提交

        String orderToken = submitVo.getOrderToken();
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] " +
                "then return redis.call('del', KEYS[1]) " +
                "else return 0 end";
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(orderToken), orderToken);
        if (!flag){
            throw new OrderException("请不要重复提交订单");
        }
        // 验价
        BigDecimal totalPrice = submitVo.getTotalPrice();
        List<OrderItemVo> items = submitVo.getItems();
         if (CollectionUtils.isEmpty(items)){
             throw new OrderException("请快去添加商品吧");
         }
        BigDecimal currentTotalPrice = items.stream().map(item->{
             Long skuId = item.getSkuId();
             ResponseVo<SkuEntity> skuEntityResponseVo = pmsClient.querySkuById(skuId);
             SkuEntity skuEntity = skuEntityResponseVo.getData();
             if (skuEntity != null) {
                 return  skuEntity.getPrice().multiply(new BigDecimal(item.getCount()));
             }
           return new BigDecimal(0);
         }).reduce((a,b)->a.add(b)).get();
         if (totalPrice.compareTo(currentTotalPrice)!=0){
             throw new OrderException("页面已更新 请刷新后重试");
         }
        // 锁库存
        List<SkuLockVo> lockVos = items.stream().map(item -> {
            SkuLockVo skuLockVo = new SkuLockVo();
            skuLockVo.setSkuId(item.getSkuId());
            skuLockVo.setCount(item.getCount());
            skuLockVo.setOrderToken(orderToken);
            return skuLockVo;
        }).collect(Collectors.toList());
        ResponseVo<List<SkuLockVo>> skuLockVoResponseVo = wmsClient.checkAndLock(lockVos);
        List<SkuLockVo> skuLockVos = skuLockVoResponseVo.getData();
        if (!CollectionUtils.isEmpty(skuLockVos)){
            throw new OrderException("手慢了，商品库存不足：" + JSON.toJSONString(skuLockVos));
        }
        // 下单
        OrderEntity orderEntity = null;
        Long userId = LoginInterceptor.getUserInfo().getUserId();
        try {
            ResponseVo<OrderEntity> orderEntityResponseVo = omsClient.saveOrder(submitVo, userId);
            orderEntity = orderEntityResponseVo.getData();
        } catch (Exception e) {
            e.printStackTrace();
            // 如果创建订单失败 立马释放库存
            rabbitTemplate.convertAndSend("ORDER-EXCHANGE","stock.unlock",orderToken);
        }

        //删除购物车。异步发送消息给购物车，删除购物车
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        List<Long> skuIds = items.stream().map(OrderItemVo::getSkuId).collect(Collectors.toList());
        map.put("skuIds",JSON.toJSONString(skuIds));
        // 发送消息给交换机 删除购物车
         rabbitTemplate.convertAndSend("ORDER-EXCHANGE","cart.delete",map);

        return orderEntity;
    }
}
