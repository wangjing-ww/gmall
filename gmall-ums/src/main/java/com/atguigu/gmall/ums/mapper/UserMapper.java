package com.atguigu.gmall.ums.mapper;

import com.atguigu.gmall.ums.entity.UserEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表
 * 
 * @author Aurora
 * @email 914375990@qq.com
 * @date 2020-07-20 19:31:31
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
	
}
