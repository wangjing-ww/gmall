package com.atguigu.gmall.index.utils;

import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class DistributeLock {

    @Autowired
    private StringRedisTemplate redisTemplate;

    //加锁
   public Boolean tryLock(String lockName,String uuid,Long expire){

        String script = "if (redis.call('exists', KEYS[1]) == 0 or redis.call('hexists', KEYS[1], ARGV[1]) == 1) " +
                "then" +
                "    redis.call('hincrby', KEYS[1], ARGV[1], 1);" +
                "    redis.call('expire', KEYS[1], ARGV[2]);" +
                "    return 1;" +
                "else" +
                "   return 0;" +
                "end";
        if (!redisTemplate.execute(new DefaultRedisScript<>(script,Boolean.class), Arrays.asList(lockName), uuid,expire.toString())){
            try {
                //没有获取到锁 重试
                Thread.sleep(2000);
                tryLock(lockName, uuid, expire);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // 给锁续期
       this.renewTime(lockName, expire);
        // 获取到锁 返回
        return true;
    }
    // 解锁
    public void unLock(String lockName,String uuid){
        String script = "if (redis.call('hexists', KEYS[1], ARGV[1]) == 0) then" +
                "    return nil;" +
                "end;" +
                "if (redis.call('hincrby', KEYS[1], ARGV[1], -1) > 0) then" +
                "    return 0;" +
                "else" +
                "    redis.call('del', KEYS[1]);" +
                "    return 1;" +
                "end;";
        /**
         * nil 代表尝试 解别的服务的锁
         * 0 表示自己的锁还没有释放完成
         * 1 代表 释放完成
         */
        Long result = redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), Arrays.asList(lockName), uuid);
        // 如果未返回值，代表尝试解其他线程的锁
        if (result == null) {
            throw new IllegalMonitorStateException("attempt to unlock lock, not locked by lockName: "
                    + lockName + " with request: "  + uuid);
        }

    }
    // 给锁设置过期时间
    public void renewTime(String lockName,Long expire){
        String script = "if redis.call('exists', KEYS[1]) == 1 then return redis.call('expire', KEYS[1], ARGV[1]) else return 0 end";
        new Thread(()->{
            while (redisTemplate.execute(new DefaultRedisScript<>(script,Boolean.class), Lists.newArrayList(lockName),expire.toString())){
                try {
                    // 到达过期时间的2/3时间，自动续期
                    Thread.sleep(expire * 2000 / 3);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }).start();

    }
}
