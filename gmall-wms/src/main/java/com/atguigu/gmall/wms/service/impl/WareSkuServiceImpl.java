package com.atguigu.gmall.wms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.wms.mapper.WareSkuMapper;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.service.WareSkuService;
import org.springframework.util.CollectionUtils;


@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuMapper, WareSkuEntity> implements WareSkuService {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private WareSkuMapper wareSkuMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;
    private static final String KEY_PREFIX = "store:lock:";
    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<WareSkuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<WareSkuEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public List<SkuLockVo> checkAndLock(List<SkuLockVo> lockVos) {
       lockVos.forEach(lockVo->{
           // 每一个商品验库存并锁库存
           checkLock(lockVo);
       });
        // 如果有一个商品锁定失败了，所有已经成功锁定的商品要解库存
        List<SkuLockVo> successLockVos = lockVos.stream().filter(SkuLockVo::getLock).collect(Collectors.toList());
        List<SkuLockVo> errorLockVos = lockVos.stream().filter(skuLockVo -> !skuLockVo.getLock()).collect(Collectors.toList());
         if (!CollectionUtils.isEmpty(successLockVos)){
            successLockVos.forEach(skuLockVo -> {
                wareSkuMapper.unLockStore(skuLockVo.getWareSkuId().longValue(),skuLockVo.getCount());
            });
            return lockVos;
         }
        //  把库存的锁定信息保存到redis中，以方便将来解锁库存
        String orderToken = lockVos.get(0).getOrderToken();
        redisTemplate.opsForValue().set(KEY_PREFIX+orderToken, JSON.toJSONString(lockVos));
        rabbitTemplate.convertAndSend("ORDER-EXCHANGE", "stock.ttl", orderToken);

        return null;// 如果都锁定成功，不需要展示锁定情况
    }
    private void checkLock(SkuLockVo skuLockVo){
        RLock lock = redissonClient.getFairLock("lock:"+skuLockVo.getSkuId());
        lock.lock();
        //验库存
       List<WareSkuEntity> wareSkuEntities =  wareSkuMapper.checkStore(skuLockVo.getSkuId(),skuLockVo.getCount());
       if (CollectionUtils.isEmpty(wareSkuEntities)){
           skuLockVo.setLock(false);
           lock.unlock();
           return;
       }
        //锁库存
        Integer result = wareSkuMapper.lockStore(wareSkuEntities.get(0).getId(),skuLockVo.getCount());
        if (result==1){
            skuLockVo.setLock(true);
            skuLockVo.setWareSkuId(wareSkuEntities.get(0).getId().intValue());
        }else {
            skuLockVo.setLock(false);
        }
    }

}