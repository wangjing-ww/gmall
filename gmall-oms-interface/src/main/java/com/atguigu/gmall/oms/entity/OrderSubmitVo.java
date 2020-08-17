package com.atguigu.gmall.oms.entity;


import com.atguigu.gmall.ums.entity.UserAddressEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderSubmitVo {
    private String orderToken; //防重
    private BigDecimal totalPrice;//总价
    private UserAddressEntity address;//用户地址
    private Integer payType;//支付类型/
    private String deliveryCompany; //快递公司/
    private List<OrderItemVo> items;//订单详情/
    private Integer bounds;// 积分信息
}
