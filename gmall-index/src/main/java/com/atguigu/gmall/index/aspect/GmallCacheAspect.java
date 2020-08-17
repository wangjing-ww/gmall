package com.atguigu.gmall.index.aspect;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class GmallCacheAspect {

    @Autowired
    RedissonClient redissonClient;
    @Autowired
    private StringRedisTemplate redisTemplate;
    /**
     * 声明一个环绕通知：
     * 		1.方法的返回值必须是Object
     * 		2.方法形参中必须有一个ProceedingJoinPoint参数（JoinPoint）
     * 		3.方法必须抛出一个Throwable异常
     * 		4.proceedingJoinPoint.proceed(proceedingJoinPoint.getArgs())执行目标方法
     */
    /**
     * jointPoint.getArgs() 获取方法参数
     * jointPoint.getTarget().getClass()获取目标类
     * @param joinPoint
     * @return
     * @throws Throwable
     */

    @Around("@annotation(com.atguigu.gmall.index.aspect.GmallCache)")
    public Object arount(ProceedingJoinPoint joinPoint) throws Throwable{

        // 获取切点方法的签名
       MethodSignature methodSignature =  (MethodSignature)joinPoint.getSignature();
        // 获取方法对象
        Method method = methodSignature.getMethod();
        //获取 方法上指定注解的对象
        GmallCache gmallCache = method.getAnnotation(GmallCache.class);
        // 获取注解中的前缀
        String prefix = gmallCache.prefix();
        //获取方法的参数
        Object[] args = joinPoint.getArgs();
        String param = Arrays.asList(args).toString();
        //获取方法的返回值类型
        Class<?> returnType = method.getReturnType();
        // 拦截当前代码块 判断缓存中有没有
        String json = redisTemplate.opsForValue().get(prefix + param);
        // 判断缓存中的数据是否为空\
        if (StringUtils.isNotBlank(json)){
            return JSON.parseObject(json, returnType);
        }
        // 没有 加分布式锁
        String lock = gmallCache.lock();
        RLock rLock = redissonClient.getLock(lock + param);
        rLock.lock();
        // 判断缓存中有没有 有直接返回(加锁的过程中，别的请求可能已经把数据放入缓存里面了)
        String json2 = redisTemplate.opsForValue().get(prefix + param);
        // 判断缓存中的数据是否为空\
        if (StringUtils.isNotBlank(json2)){
            return JSON.parseObject(json2, returnType);
        }

        //执行目标方法
        Object result = joinPoint.proceed(joinPoint.getArgs());

        // 拦截代码块：放入缓存 释放分布式锁
        int timeout = gmallCache.timeout();
        int random = gmallCache.random();
        redisTemplate.opsForValue().set(prefix+param, JSON.toJSONString(result),timeout+new Random().nextInt(random), TimeUnit.SECONDS);
        rLock.unlock();

        return result;



    }
}
