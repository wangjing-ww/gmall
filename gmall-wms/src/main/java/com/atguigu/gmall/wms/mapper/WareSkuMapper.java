package com.atguigu.gmall.wms.mapper;

import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


import java.util.List;

/**
 * 商品库存
 * 
 * @author Aurora
 * @email 914375990@qq.com
 * @date 2020-07-20 19:34:14
 */
@Mapper
public interface WareSkuMapper extends BaseMapper<WareSkuEntity> {

    /**
     * 验证库存
     * @param skuId
     * @param count
     * @return
     */
    List<WareSkuEntity> checkStore(@Param("skuId") Long skuId,@Param("count") Integer count);

    Integer lockStore(@Param("id")Long id, @Param("count")Integer count);

    Integer unLockStore(@Param("id")Long id,@Param("count") Integer count);

    void minus(Long skuId, Integer count);

}
