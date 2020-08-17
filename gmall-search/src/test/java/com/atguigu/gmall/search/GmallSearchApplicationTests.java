package com.atguigu.gmall.search;

import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.feign.GmallPmsClient;
import com.atguigu.gmall.search.feign.GmallWmsClient;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchAttrValue;
import com.atguigu.gmall.search.repository.GoodsRepository;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.util.CollectionUtils;


import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
class GmallSearchApplicationTests {
    @Autowired
    ElasticsearchRestTemplate restTemplate;
    @Autowired
    GoodsRepository goodsRepository;
    @Autowired
    GmallPmsClient pmsClient;
    @Autowired
    GmallWmsClient wmsClient;

    /**
     * 自己写的
     */
    @Test
    void contextLoads() {

        // 创建索引库和映射
        restTemplate.createIndex(Goods.class);
        restTemplate.putMapping(Goods.class);

        Integer pageNum = 1;
        Integer pageSize = 100;
        do {
            PageParamVo paramVo = new PageParamVo();
            paramVo.setPageNum(pageNum);
            paramVo.setPageSize(pageSize);
            ResponseVo<List<SpuEntity>> spusByPage = pmsClient.querySpusByPage(paramVo);
            List<SpuEntity> spus = spusByPage.getData();
            spus.forEach(spuEntity -> {
                ResponseVo<List<SkuEntity>> skuResposityVo = this.pmsClient.querySkusBySpuId(spuEntity.getId());
                List<SkuEntity> skuEntities = skuResposityVo.getData();
                if (!CollectionUtils.isEmpty(skuEntities)){
                   List<Goods> goodsList =  skuEntities.stream().map(skuEntity -> {
                        Goods goods = new Goods();
                       // 查询spu搜索属性及值
                       ResponseVo<List<SpuAttrValueEntity>> spuAttrValueResponseVo = this.pmsClient.querySearchAttrValueBySpuId(spuEntity.getId());
                       List<SpuAttrValueEntity> spuAttrValueEntities = spuAttrValueResponseVo.getData();
                       List<SearchAttrValue> searchAttrValues = new ArrayList<>();
                       if (!CollectionUtils.isEmpty(spuAttrValueEntities)){
                           searchAttrValues = spuAttrValueEntities.stream().map(spuAttrValueEntity -> {
                               SearchAttrValue searchAttrValue = new SearchAttrValue();
                               searchAttrValue.setAttrId(spuAttrValueEntity.getAttrId());
                               searchAttrValue.setAttrName(spuAttrValueEntity.getAttrName());
                               searchAttrValue.setAttrValue(spuAttrValueEntity.getAttrValue());
                               return searchAttrValue;
                           }).collect(Collectors.toList());
                       }
                       // 查询sku搜索属性及值
                       ResponseVo<List<SkuAttrValueEntity>>  skuAttrValueResponseVo = this.pmsClient.querySearchAttrValueBySkuId(skuEntity.getId());
                       List<SkuAttrValueEntity> skuAttrValueEntities = skuAttrValueResponseVo.getData();
                       List<SearchAttrValue> searchSkuAttrValues = new ArrayList<>();
                       if (!CollectionUtils.isEmpty(skuAttrValueEntities)){
                           searchSkuAttrValues = skuAttrValueEntities.stream().map(skuAttrValueEntity -> {
                               SearchAttrValue searchAttrValue = new SearchAttrValue();
                               searchAttrValue.setAttrId(skuAttrValueEntity.getAttrId());
                               searchAttrValue.setAttrName(skuAttrValueEntity.getAttrName());
                               searchAttrValue.setAttrValue(skuAttrValueEntity.getAttrValue());
                               return searchAttrValue;
                           }).collect(Collectors.toList());
                       }
                       searchAttrValues.addAll(searchSkuAttrValues);
                       goods.setSearchAttrs(searchAttrValues);

                       // 查询品牌
                       ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(skuEntity.getBrandId());
                       BrandEntity brandEntity = brandEntityResponseVo.getData();
                      if (brandEntity!=null){
                         goods.setBrandId(skuEntity.getBrandId());
                         goods.setBrandName(brandEntity.getName());
                      }

                      // 查询分类
                       ResponseVo<CategoryEntity> categoryEntityResponseVo = this.pmsClient.queryCategoryById(skuEntity.getCatagoryId());
                       CategoryEntity categoryEntity = categoryEntityResponseVo.getData();
                       if (categoryEntity != null){
                           goods.setCategoryId(skuEntity.getCatagoryId());
                           goods.setCategoryName(categoryEntity.getName());
                       }

                       goods.setPrice(skuEntity.getPrice().doubleValue());
                       goods.setCreateTime(spuEntity.getCreateTime());
                       goods.setDefaultImage(skuEntity.getDefaultImage());
                       goods.setSales(0l);
                       goods.setSkuId(skuEntity.getId());

                       // 查询 库存信息
                       ResponseVo<List<WareSkuEntity>> listResponseVo = this.wmsClient.queryWareSkuBySkuId(skuEntity.getId());
                       List<WareSkuEntity> wareSkuEntities = listResponseVo.getData();
                       if (!CollectionUtils.isEmpty(wareSkuEntities)){
                           boolean flag = wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() -wareSkuEntity.getStockLocked() >0);
                           goods.setStore(flag);
                       }
                       goods.setTitle(skuEntity.getTitle());
                       goods.setSubTitle(skuEntity.getSubtitle());
                       return goods;
                    }).collect(Collectors.toList());
                    this.goodsRepository.saveAll(goodsList);
                }
            });
            pageSize = spus.size();
            pageNum++;
        }while (pageSize == 100);
    }
    /*
          老师源码
     */
    @Test
    void importData(){

        // 创建索引及映射
        this.restTemplate.createIndex(Goods.class);
        this.restTemplate.putMapping(Goods.class);

        Integer pageNum = 1;
        Integer pageSize = 100;

        do {
            // 分页查询spu
            PageParamVo pageParamVo = new PageParamVo();
            pageParamVo.setPageNum(pageNum);
            pageParamVo.setPageSize(pageSize);
            ResponseVo<List<SpuEntity>> spuResp = this.pmsClient.querySpusByPage(pageParamVo);
            List<SpuEntity> spus = spuResp.getData();

            // 遍历spu，查询sku
            spus.forEach(spuEntity -> {
                ResponseVo<List<SkuEntity>> skuResp = this.pmsClient.querySkusBySpuId(spuEntity.getId());
                List<SkuEntity> skuEntities = skuResp.getData();
                if (!CollectionUtils.isEmpty(skuEntities)){
                    // 把sku转化成goods对象
                    List<Goods> goodsList = skuEntities.stream().map(skuEntity -> {
                        Goods goods = new Goods();

                        // 查询spu搜索属性及值
                        ResponseVo<List<SpuAttrValueEntity>> attrValueResp = this.pmsClient.querySearchAttrValueBySpuId(spuEntity.getId());
                        List<SpuAttrValueEntity> attrValueEntities = attrValueResp.getData();
                        List<SearchAttrValue> searchAttrValues = new ArrayList<>();
                        if (!CollectionUtils.isEmpty(attrValueEntities)) {
                            searchAttrValues = attrValueEntities.stream().map(spuAttrValueEntity -> {
                                SearchAttrValue searchAttrValue = new SearchAttrValue();
                                searchAttrValue.setAttrId(spuAttrValueEntity.getAttrId());
                                searchAttrValue.setAttrName(spuAttrValueEntity.getAttrName());
                                searchAttrValue.setAttrValue(spuAttrValueEntity.getAttrValue());
                                return searchAttrValue;
                            }).collect(Collectors.toList());
                        }
                        // 查询sku搜索属性及值
                        ResponseVo<List<SkuAttrValueEntity>> skuAttrValueResp = this.pmsClient.querySearchAttrValueBySkuId(spuEntity.getId());
                        List<SkuAttrValueEntity> skuAttrValueEntities = skuAttrValueResp.getData();
                        List<SearchAttrValue> searchSkuAttrValues = new ArrayList<>();
                        if (!CollectionUtils.isEmpty(skuAttrValueEntities)) {
                            searchSkuAttrValues = skuAttrValueEntities.stream().map(skuAttrValueEntity -> {
                                SearchAttrValue searchAttrValue = new SearchAttrValue();
                                searchAttrValue.setAttrId(skuAttrValueEntity.getAttrId());
                                searchAttrValue.setAttrName(skuAttrValueEntity.getAttrName());
                                searchAttrValue.setAttrValue(skuAttrValueEntity.getAttrValue());
                                return searchAttrValue;
                            }).collect(Collectors.toList());
                        }
                        searchAttrValues.addAll(searchSkuAttrValues);
                        goods.setSearchAttrs(searchAttrValues);

                        // 查询品牌
                        ResponseVo<BrandEntity> brandEntityResp = this.pmsClient.queryBrandById(skuEntity.getBrandId());
                        BrandEntity brandEntity = brandEntityResp.getData();
                        if (brandEntity != null){
                            goods.setBrandId(skuEntity.getBrandId());
                            goods.setBrandName(brandEntity.getName());
                        }

                        // 查询分类
                        ResponseVo<CategoryEntity> categoryEntityResp = this.pmsClient.queryCategoryById(skuEntity.getCatagoryId());
                        CategoryEntity categoryEntity = categoryEntityResp.getData();
                        if (categoryEntity != null) {
                            goods.setCategoryId(skuEntity.getCatagoryId());
                            goods.setCategoryName(categoryEntity.getName());
                        }

                        goods.setCreateTime(spuEntity.getCreateTime());
                        goods.setDefaultImage(skuEntity.getDefaultImage());
                        goods.setPrice(skuEntity.getPrice().doubleValue());
                        goods.setSales(0l);
                        goods.setSkuId(skuEntity.getId());

                        // 查询库存信息
                        ResponseVo<List<WareSkuEntity>> listResp = this.wmsClient.queryWareSkuBySkuId(skuEntity.getId());
                        List<WareSkuEntity> wareSkuEntities = listResp.getData();
                        if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                            boolean flag = wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() > 0);
                            goods.setStore(flag);
                        }
                        goods.setTitle(skuEntity.getTitle());
                        return goods;
                    }).collect(Collectors.toList());

                    // 导入索引库
                    this.goodsRepository.saveAll(goodsList);
                }
            });

            pageSize = spus.size();
            pageNum++;

        } while (pageSize == 100);

    }
}
