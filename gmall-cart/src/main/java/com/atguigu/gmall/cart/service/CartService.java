package com.atguigu.gmall.cart.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.feign.GmallSmsClient;
import com.atguigu.gmall.cart.feign.GmallWmsClient;
import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.bean.UserInfo;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.concurrent.ListenableFuture;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CartService {

    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallWmsClient wmsClient;
    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CartAsyncService cartAsyncService;
    private static final String KEY_PREFIX = "cart:info:";
    private static final String PRICE_PREFIX = "cart:price:";

    /**
     * 添加购物车
     * @param cart
     */
    public void addCart(Cart cart) {

        // 1 获取用户登入的信息
        String userId = getUserId();
        String key = KEY_PREFIX+userId;
        // 2 获取redis中 该用户的购物车
        BoundHashOperations<String, Object, Object> hashOps = redisTemplate.boundHashOps(key);

        // 3 判断该用户的购物车信息 是否已经包含了商品的信息
        String skuId = cart.getSkuId().toString();
        // 用户添加的商品数量
        BigDecimal count = cart.getCount();
        if (hashOps.hasKey(skuId)){
            // 4 包含，更新数量
            String cartJson = hashOps.get(skuId).toString();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(cart.getCount().add(count));
            // 更新mysql数据库
            cartAsyncService.updateCartByUserIdAndSkuId(userId,cart);

        }else {
            // 5 不包含 给用户新增购物车记录skuId
             cart.setUserId(userId);
             // 根据skuId 查询sku
            ResponseVo<SkuEntity> skuEntityResponseVo = pmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity != null) {
                cart.setTitle(skuEntity.getTitle());
                cart.setPrice(skuEntity.getPrice());
                cart.setImage(skuEntity.getDefaultImage());
            }

            // 根据skuId查询 销售属性
            ResponseVo<List<SkuAttrValueEntity>> skuAttrValueResponseVo = pmsClient.querySearchAttrValueBySkuId(cart.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntities = skuAttrValueResponseVo.getData();
            cart.setSaleAttrs(JSON.toJSONString(skuAttrValueEntities));

            // 根据skuId查询营销信息
            ResponseVo<List<ItemSaleVo>> itemResponseVo = smsClient.querySalesBySkuId(cart.getSkuId());
            List<ItemSaleVo> saleVos = itemResponseVo.getData();
            cart.setSales(JSON.toJSONString(saleVos));

            // 根据skuID查询 库存信息
            ResponseVo<List<WareSkuEntity>> wareSkuResponseVo = wmsClient.queryWareSkuBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = wareSkuResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)){
                cart.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked()>0));
            }
            cart.setCheck(true);
            cartAsyncService.saveCart(userId,cart);
            //缓存 价格到redis中
            redisTemplate.opsForValue().set(PRICE_PREFIX+skuId, skuEntity.getPrice().toString());
        }
        hashOps.put(skuId, JSON.toJSONString(cart));


    }

    /**
     * 获取userId
     * @return
     */
    private String getUserId() {
        String userId = null;
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        if (userInfo.getUserId()!=null){
            userId = userInfo.getUserId().toString();
        }else {
            userId = userInfo.getUserKey();
        }
        return userId;
    }

    /**
     * 查询 sku 对应的购物车
     * @param skuId
     * @return
     */
    public Cart queryCartBySkuId(Long skuId) {
        String userId = getUserId();
        String key  = KEY_PREFIX+userId;
        //  从redis中获取 该用户的的购物车
        BoundHashOperations<String, Object, Object> hashOps = redisTemplate.boundHashOps(key);
        if (hashOps.hasKey(skuId.toString())){
            String cartJson = hashOps.get(skuId.toString()).toString();
            Cart cart = JSON.parseObject(cartJson, Cart.class);

            return cart;
        }
           throw new RuntimeException("您的 购物车 没有该商品的记录");
    }

    /**
     * 查询 所有的购物车
     * @return
     */
    public List<Cart> queryCarts() {
        // 查询未登录的购物车
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        String userKey = userInfo.getUserKey();
       // String userId = getUserId();
        String unLoginKey  = KEY_PREFIX+userKey;
        BoundHashOperations<String, Object, Object> unLoginHashOps = redisTemplate.boundHashOps(unLoginKey);
        List<Object> unLoginCartsJson = unLoginHashOps.values();
        List<Cart> unLoginCarts = null;
        if (!CollectionUtils.isEmpty(unLoginCartsJson)){
            unLoginCarts =  unLoginCartsJson.stream().map(cartJson->{
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                // 查询实时价格
                String currentPriceString = redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId());
                cart.setCurrentPrice(new BigDecimal(currentPriceString));
                return cart;
            }).collect(Collectors.toList());
        }
          // 获取未登入 状态 未登入直接返回
        if (userInfo.getUserId() == null){
            return unLoginCarts;
        }
        // 合并到登入状态的购物车
        String loginKey = KEY_PREFIX+userInfo.getUserId();
        BoundHashOperations<String, Object, Object> loginHashOps = redisTemplate.boundHashOps(loginKey);
        if (!CollectionUtils.isEmpty(unLoginCarts)){
            unLoginCarts.forEach(cart -> {
                String skuId = cart.getSkuId().toString();
                if (loginHashOps.hasKey(skuId)){
                    String cartJson = loginHashOps.get(skuId).toString();
                    BigDecimal count = cart.getCount();
                    cart = JSON.parseObject(cartJson, Cart.class);
                    cart.setCount(cart.getCount().add(count));

                    // 异步更新到数据库
                    cartAsyncService.updateCartByUserIdAndSkuId(cart.getUserId(),cart);
                }else {
                    // 新增 sql
                    cart.setUserId(userInfo.getUserId().toString());
                    cartAsyncService.saveCart(cart.getUserId(),cart);
                }
                // 更新到 redis
                loginHashOps.put(skuId, JSON.toJSONString(cart));
            });
            // 删除 未登录的购物车 删除redis和mysql中未登入的购物车
            redisTemplate.delete(unLoginKey);
            cartAsyncService.deleteCartsByUserId(userInfo.getUserKey());
        }
        // 查询登录的购物车状态并返回
        List<Object> loginCartsJson = loginHashOps.values();
        if (CollectionUtils.isEmpty(loginCartsJson)){
            return null;
        }
        List<Cart> carts = loginCartsJson.stream().map(cartJson -> {
            Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
            // 查询实时价格
            String currentPriceString = redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId());
            cart.setCurrentPrice(new BigDecimal(currentPriceString));
            return cart;
        }).collect(Collectors.toList());
        return carts;
    }

    /**
     * 更新购物车商品的数量
     * @param cart
     */
    public void updateNum(Cart cart) {
        String userId = getUserId();
        String key = KEY_PREFIX+userId;
        BoundHashOperations<String, Object, Object> hashOps = redisTemplate.boundHashOps(key);
        if (hashOps.hasKey(cart.getSkuId().toString())){
            String cartJson = hashOps.get(cart.getSkuId().toString()).toString();
            BigDecimal count = cart.getCount();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(cart.getCount().add(count));
            // 更新redis
            cartAsyncService.updateCartByUserIdAndSkuId(userId,cart);
            //更新 mysql
            hashOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
        }
    }

    /**
     * 删除购物车
     * @param skuId
     */
    public void deleteCart(Long skuId) {
        String userId = getUserId();
        String key = KEY_PREFIX+userId;
        BoundHashOperations<String, Object, Object> hashOps = redisTemplate.boundHashOps(key);
        if (hashOps.hasKey(skuId.toString())){
            cartAsyncService.deleteCartByUserIdAndSkuId(userId,skuId);
            hashOps.delete(skuId.toString());
        }
    }

    @Async
    public String executor1(){
        try {
            System.out.println("executor1 开始执行了");
            TimeUnit.SECONDS.sleep(3);
            System.out.println("executor1 执行完成了");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "hello exector1";
    }

    @Async
    public String executor2(){
        try {
            System.out.println("executor2 开始执行了");
            TimeUnit.SECONDS.sleep(5);
            System.out.println("executor2 执行完成了");
            int i = 1/0;
            return "hello executor2";
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Async
    public ListenableFuture<String> executor3(){
        try {
            System.out.println("executor3 开始执行了");
            TimeUnit.SECONDS.sleep(3);
            System.out.println("executor3 执行完成了");
            return AsyncResult.forValue("executor3");
        } catch (InterruptedException e) {
          //  e.printStackTrace();
            return AsyncResult.forExecutionException(e);
        }

    }

    @Async
    public ListenableFuture<String> executor4(){
        try {
            System.out.println("executor4 开始执行了");
            TimeUnit.SECONDS.sleep(5);
            System.out.println("executor4 执行完成了");

            int i = 1/0;  // 制造异常
            return AsyncResult.forValue("hello exector4") ;

        } catch (InterruptedException e) {
            e.printStackTrace();
            return AsyncResult.forExecutionException(e);
        }

    }


    public List<Cart> queryCheckedCarts(Long userId) {
        String key = KEY_PREFIX+userId;
        BoundHashOperations<String, Object, Object> hashOps = redisTemplate.boundHashOps(key);
        List<Object> cartJsons = hashOps.values();
        if (CollectionUtils.isEmpty(cartJsons)){
            return null;
        }
        List<Cart> carts = cartJsons.stream().map(cartJson ->
                JSON.parseObject(cartJson.toString(), Cart.class)
        ).filter(cart -> cart.getCheck()).collect(Collectors.toList());
        return carts;
    }
}
