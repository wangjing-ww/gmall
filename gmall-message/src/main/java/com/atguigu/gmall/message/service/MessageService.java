package com.atguigu.gmall.message.service;

import com.aliyuncs.exceptions.ClientException;
import org.springframework.stereotype.Service;


public interface MessageService {
    /**
     * 发送短信
     * @param mobile 电话号码
     * @param checkCode 验证码
     * @throws ClientException
     */
    void send(String mobile, String checkCode) throws ClientException;
}
