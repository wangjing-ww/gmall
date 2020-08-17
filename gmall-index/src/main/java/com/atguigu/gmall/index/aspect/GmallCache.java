package com.atguigu.gmall.index.aspect;

import java.lang.annotation.*;

@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface GmallCache {
    /**
     * 缓存时 的前缀
     * @return
     */
    String prefix() default "";

    /**
     * 单位：分钟
     * 设置缓存的有效时间
     * @return
     */
    int timeout() default 5;

    /**
     * 防止雪崩设置的随机值范围
     * @return
     */
    int random() default 5;

    /**
     * 防止击穿 分布式锁的key
     * @return
     */
    String lock() default "lock";

}
