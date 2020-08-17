package com.atguigu.gmall.auth.service;

import com.atguigu.gmall.auth.config.JwtProperties;
import com.atguigu.gmall.auth.feign.GmallUmsClient;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.utils.CookieUtils;
import com.atguigu.gmall.common.utils.IpUtil;
import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.common.utils.UserException;
import com.atguigu.gmall.ums.entity.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@Service
@EnableConfigurationProperties({JwtProperties.class})
public class AuthService {

    @Autowired
    private GmallUmsClient umsClient;
    @Autowired
    private JwtProperties jwtProperties;
    public void accredit(String loginName, String password, HttpServletRequest request, HttpServletResponse response) {
        try {
            ResponseVo<UserEntity> userEntityResponseVo = umsClient.queryUser(loginName, password);
            UserEntity userEntity = userEntityResponseVo.getData();
            // 2判断用户信息是否为空
            if (userEntity == null) {
                throw new UserException("用户名或者密码错误！！！");
            }
            //3 把用户id及用户名 放入载荷
            Map<String, Object> map = new HashMap<>();
            map.put("userId", userEntity.getId());
            map.put("username", userEntity.getUsername());
            //4 为了防止jwt被别人盗取 载荷中加入 ip地址
            String ip = IpUtil.getIpAddressAtService(request);
            map.put("ip", ip);

            // 5 制作jwt类型的token
            String token = JwtUtils.generateToken(map, jwtProperties.getPrivateKey(), jwtProperties.getExpire());
            // 6 把jwt 放入cookie
            CookieUtils.setCookie(request, response, jwtProperties.getCookieName(), token, jwtProperties.getExpire()*60 );
            // 7用户昵称 放入cookie中 方便页面展示
            CookieUtils.setCookie(request, response, jwtProperties.getUnick(), userEntity.getNickname(), jwtProperties.getExpire()*60);
        } catch (Exception e){
             throw new UserException("用户名或者密码错误"+e.getMessage());
        }


    }
}
