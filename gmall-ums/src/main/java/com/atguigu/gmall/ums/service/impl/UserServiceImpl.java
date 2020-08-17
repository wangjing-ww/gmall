package com.atguigu.gmall.ums.service.impl;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.ums.feign.GmallMessageClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;


import org.omg.CORBA.UserException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;

import java.util.UUID;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.ums.mapper.UserMapper;
import com.atguigu.gmall.ums.entity.UserEntity;
import com.atguigu.gmall.ums.service.UserService;
import org.springframework.util.CollectionUtils;


@Service("userService")
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    @Autowired
    RedisTemplate redisTemplate;
    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<UserEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<UserEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public Boolean checkData(String data, Integer type) {
        QueryWrapper<UserEntity> queryWrapper = new QueryWrapper<>();
        switch (type){
            case 1:
                queryWrapper.eq("username", data);
                break;
            case 2:
                queryWrapper.eq("phone", data);
                break;
            case 3:
                queryWrapper.eq("email", data);
                break;
            default:return null;
        }
        return this.baseMapper.selectCount(queryWrapper)==0;

    }

    @Autowired
    GmallMessageClient messageClient;
    @Override
    public void register(UserEntity userEntity, String code) {
        // 发送短信
      /*     messageClient.getCode(userEntity.getPhone());
        //Object data = clientCode.getData();
        // 校验短信验证码

         String cacheCode = (String) this.redisTemplate.opsForValue().get(userEntity.getPhone());
         if (!StringUtils.equals(code, cacheCode)) {
              return;
         }*/


        //生成盐
        String salt = StringUtils.replace(UUID.randomUUID().toString(), "-", "");

        userEntity.setSalt(salt);
        //md5加密

        userEntity.setPassword(DigestUtils.md5Hex(salt+DigestUtils.md5(userEntity.getPassword())));
        //设置默认参数
        userEntity.setCreateTime(new Date());
        userEntity.setLevelId(1L);
        userEntity.setStatus(1);
        userEntity.setIntegration(0);
        userEntity.setGrowth(0);
        userEntity.setNickname(userEntity.getUsername());
        //添加到数据库
        this.save(userEntity);
        // 注册成功 删除redis中记录的code
    }

    @Override
    public UserEntity queryUser(String loginName, String password) {
        UserEntity userEntity =   this.getOne(new QueryWrapper<UserEntity>().eq("username", loginName)
        .or().eq("phone", loginName)
        .or().eq("email", loginName));

        if (userEntity == null) {
           // System.out.println("账户输入不合法");
            log.warn("账户输入不合法");
        }
        password= DigestUtils.md5Hex(password + userEntity.getSalt());
        if (!StringUtils.equals(userEntity.getPassword(), password)){
            log.error("密码输入错误！！！");
        }
        return userEntity;
    }

}