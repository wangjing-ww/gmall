package com.atguigu.gmall.index.service;




import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.aspect.GmallCache;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.index.utils.DistributeLock;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.apache.commons.lang.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {
    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private DistributeLock distributeLock;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    RedissonClient redissonClient;

    public static final String KEY_PREFIX = "index:category:";

    public List<CategoryEntity> queryLevelOneCategories() {

        ResponseVo<List<CategoryEntity>> listResponseVo = pmsClient.queryCategroy(0l);
        return listResponseVo.getData();

    }

    @GmallCache(prefix = "index:cates:",timeout = 14400,random = 3600,lock = "lock")
    public List<CategoryEntity> queryLevelTwoCategoriesWithSub2(Long pid) {

        ResponseVo<List<CategoryEntity>> listResponseVo = pmsClient.queryCategoriesWithSub(pid);
        return listResponseVo.getData();
    }

    public List<CategoryEntity> queryLevelTwoCategoriesWithSub(Long pid) {

        String cacheCategories = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (StringUtils.isNotBlank(cacheCategories)){
            List<CategoryEntity> categoryEntities = JSON.parseArray(cacheCategories,CategoryEntity.class);
            return categoryEntities;
        }
        ResponseVo<List<CategoryEntity>> listResponseVo = pmsClient.queryCategoriesWithSub(pid);
       this.redisTemplate.opsForValue().set(KEY_PREFIX+pid, JSON.toJSONString(listResponseVo.getData()),30, TimeUnit.DAYS);
        return listResponseVo.getData();
    }
     public void testRedissonLock(){
        //  只要锁的名称相同就是同一把锁
         // 获取锁
         RLock lock = redissonClient.getLock("lock");
         // 加锁
        // lock.lock();
         // 设置超时时间
          lock.lock(3, TimeUnit.SECONDS);
         // 查询redis中的num值
         String value = this.redisTemplate.opsForValue().get("num");
         // 没有该值return
         if (StringUtils.isBlank(value)) {
             return;
         }
         // 有值就转成成int
         int num = Integer.parseInt(value);
         // 把redis中的num值+1
         this.redisTemplate.opsForValue().set("num", String.valueOf(++num));
         // 解锁
         lock.unlock();

     }
    public void testLock() {

        //1 设置锁 从redis中获取锁 setnx
          // 设置过期时间：如果在执行业务的时候出现异常，会导致锁无法释放，设置过期时间，自动释放锁
        /**
         * 多个微服务 一同运行的时候 可能会释放别的 服务的锁
         * 如果 运行总时间是7s
         * index1 获取到锁之后 业务没有执行完成 3s后锁自动释放
         * index2 获取到锁后 业务执行完成 3s后自动释放锁
         * index3 获取到锁后，执行业务
         * index1 业务执行完成 开始调用del释放锁 将index3的锁 释放了，导致index3 执行1s锁就被释放了
         * 最后 index3 没有锁了
         *
         * 解决：为每一把锁设置一个唯一的uuid
         */
        String uuid = UUID.randomUUID().toString();
        // 尝试获取锁
        Boolean lock = distributeLock.tryLock("lock", uuid, 300l);
        //
      //  Boolean lock = redisTemplate.opsForValue().setIfAbsent("lock", uuid,3,TimeUnit.SECONDS);
        if (lock){

            // 2 执行业务
            String value = redisTemplate.opsForValue().get("num");
            if (StringUtils.isBlank(value)){
                return;
            }
            int num = Integer.parseInt(value);
            redisTemplate.opsForValue().set("num", String.valueOf(++num));

            // 测试 可重入性

           // this.testSubLock(uuid);

            // 睡眠60s，锁过期时间30s。每隔20s自动续期
            try {
                TimeUnit.SECONDS.sleep(60);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // 3释放锁

            distributeLock.unLock("lock", uuid);

            /**
             * 在执行删除操作的时候 缺乏原子性
             * 例： index1在执行删除操作时 过期时间已到，锁自动释放了
             *      index2获取锁 执行业务操作
             *      index1 执行删除操作 会将index2的锁给释放掉
             *      解决方案：
             *      执行lua脚本 解决该问题 保证原子性
             */

//            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
//             redisTemplate.execute(new DefaultRedisScript<>(script), Arrays.asList("lock"), uuid);
//            if (StringUtils.equals(redisTemplate.opsForValue().get("lock"), uuid)){
//                this.redisTemplate.delete("lock");
//            }

        }else {
            try {
                // 4 每隔一秒 再次去获取锁
                Thread.sleep(1000);
                testLock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    //  测试可重入性
    public void testSubLock(String uuid){

        //加锁
        Boolean lock = distributeLock.tryLock("lock", uuid, 300l);
        if (lock){
            System.out.println("这是 分布式  可重入锁");
            //解锁
            distributeLock.unLock("lock", uuid);
        }


    }

    public String testWrite() {
        // 获取读写锁
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");
        // 加锁
        rwLock.writeLock().lock(10, TimeUnit.SECONDS);
        System.out.println("正在 写入。。。。。");
        //解锁
        rwLock.writeLock().unlock();

        return "写入成功............";

    }

    public String testRead() {
        // 和写锁 获取的时同一把锁
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");
        // 加锁 并且设置超时时间 防止死锁
        rwLock.readLock().lock(10, TimeUnit.SECONDS);
        // 执行 业务逻辑
        System.out.println("正在 读取。。。");
        // 解锁
        rwLock.readLock().lock();
        return "读取完成。。。。。";
    }


}
