package com.atguigu.gmall.oms.mapper;

import com.atguigu.gmall.oms.entity.OrderEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单
 * 
 * @author Aurora
 * @email 914375990@qq.com
 * @date 2020-07-20 19:23:20
 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {

    int closeOrder(String orderToken);

    int successOrder(String orderToken);

}
