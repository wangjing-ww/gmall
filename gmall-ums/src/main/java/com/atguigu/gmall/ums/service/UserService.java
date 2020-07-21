package com.atguigu.gmall.ums.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.ums.entity.UserEntity;

import java.util.Map;

/**
 * 用户表
 *
 * @author Aurora
 * @email 914375990@qq.com
 * @date 2020-07-20 19:31:31
 */
public interface UserService extends IService<UserEntity> {

    PageResultVo queryPage(PageParamVo paramVo);
}

