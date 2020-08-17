package com.atguigu.gmall.message.controller;


import com.aliyuncs.exceptions.ClientException;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.utils.FormUtils;
import com.atguigu.gmall.common.utils.RandomUtils;
import com.atguigu.gmall.message.service.MessageService;
import org.springframework.data.redis.core.RedisTemplate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("message")
public class MessageController {
    @Autowired
    MessageService messageService;
    @Autowired
    private RedisTemplate redisTemplate;
//
//    @Autowired
//    private StringRedisTemplate redisTemplate;
    @GetMapping("send/{mobile}")
    public ResponseVo<Object> getCode(@PathVariable String mobile)  {

        try {
            //校验手机号是否合法
            if(StringUtils.isEmpty(mobile) || !FormUtils.isMobile(mobile)) {
                log.error("请输入正确的手机号码 ");
                //throw new GuliException(ResultCodeEnum.LOGIN_PHONE_ERROR);
            }
            //生成验证码
            String checkCode = RandomUtils.getFourBitRandom();
            //发送验证码
            System.out.println("checkCode = " + checkCode);
            messageService.send(mobile, checkCode);
            //将验证码存入redis缓存
            redisTemplate.opsForValue().set(mobile, checkCode, 5, TimeUnit.MINUTES);
        } catch (ClientException e) {
            e.printStackTrace();
        }

        return ResponseVo.ok();
    }
}
