package com.atguigu.gmall.wms.vo;

import lombok.Data;

@Data
public class SkuLockVo {
    private Long skuId; // 锁定商品的id
    private Boolean lock; // 锁定状态
    private Integer count;  //商品的数量
    private Integer wareSkuId;//商品所在仓库的id
    private String orderToken;// 放便redis缓存信息
}
