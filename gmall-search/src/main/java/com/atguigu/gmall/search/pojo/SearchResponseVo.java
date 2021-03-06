package com.atguigu.gmall.search.pojo;

import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;

import lombok.Data;

import java.util.List;

@Data
public class SearchResponseVo {
    // 过滤
    private List<BrandEntity> brands;
    private List<CategoryEntity> categories;
    // 规格：[{attrId: 8, attrName: "内存", attrValues: ["8G", "12G"]}, {attrId: 9, attrName: "机身存储", attrValues: ["128G", "256G"]}]
    private List<SearchResponseAttrVo> filters;

    // 分页
    private Integer pageNum;
    private Integer pageSize;
    private Long total;

    // 当前页数据
    private List<Goods> goodsList;
}
