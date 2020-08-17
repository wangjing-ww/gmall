package com.atguigu.gmall.item.vo;

import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.entity.SkuImagesEntity;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ItemVo {

    //三级分类
    private List<CategoryEntity> categoryEntities;
    // 品牌
    private Long brandId;
    private String brandName;
    //spu
    private Long spuId;
    private String spuName;
    // sku
    private Long skuId;
    private String title;
    private String subTitle;
    private BigDecimal price;
    private Integer weight;
    private String defaultImage;

    //sku图片
    private List<SkuImagesEntity> images;

    // 营销信息
    private List<ItemSaleVo> sales;

    //是否有货
    private Boolean store = false;

    // sku所属spu下的所有sku的销售属性
    // [{attrId:3,attrName:'颜色',attrValues:'白色','黑色','金色'}]
    // [{attrId:4,attrName:'内存',attrValues:'6G','8G','12G'}]
    // [{attrId:5,attrName:'存储',attrValues:'128G','256G',''512G}]
    private List<SaleAttrValueVo> saleAttrs;

    // 当前sku的销售 属性
    private Map<Long,String> saleAttr;

    // sku列表
    private String skusJson;

    // spu的海报信息
    private List<String> spuImages;

    // 规格参数组 及组下的规格参数（带值）
    private List<ItemGroupVo> groups;
}
