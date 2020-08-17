package com.atguigu.gmall.scheduled.jobHandler;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.scheduled.mapper.CartMapper;
import com.atguigu.gmall.scheduled.pojo.Cart;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundListOperations;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Component
public class CartJobHandler {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    CartMapper cartMapper;
    private static final String KEY = "cart:async:exception";
    private static final String KEY_PREFIX = "cart:info:";
    @XxlJob("cartJobHandler")
    public ReturnT<String> executor(){

        BoundListOperations<String, String> listOps = redisTemplate.boundListOps(KEY);
        if (listOps.size()==0){
            return ReturnT.SUCCESS;
        }
        String userId = listOps.rightPop();
        while (StringUtils.isNotBlank(userId)){
            //先删除数据库里面异常数据
            this.cartMapper.delete(new UpdateWrapper<Cart>().eq("user_id", userId));

            //再查询redis中的购物车
            List<Object> cartJsons = redisTemplate.boundHashOps(KEY_PREFIX + userId).values();
            if (CollectionUtils.isEmpty(cartJsons)){
                continue;
            }
            cartJsons.forEach(cartJson->{
                cartMapper.insert(JSON.parseObject(cartJson.toString(), Cart.class));
            });

            //下一个用户
            userId = listOps.rightPop();
        }

        return ReturnT.SUCCESS;
    }
}
