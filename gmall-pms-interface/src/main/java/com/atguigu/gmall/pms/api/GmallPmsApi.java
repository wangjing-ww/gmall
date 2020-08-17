package com.atguigu.gmall.pms.api;


import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import java.util.List;

public interface GmallPmsApi {

        @PostMapping("pms/spu/page")
        public ResponseVo<List<SpuEntity>> querySpusByPage(@RequestBody PageParamVo pageParamVo);

        @GetMapping("pms/sku/spu/{spuId}")
        public ResponseVo<List<SkuEntity>> querySkusBySpuId(@PathVariable("spuId")Long spuId);

        @GetMapping("pms/category/{id}")
        public ResponseVo<CategoryEntity> queryCategoryById(@PathVariable("id") Long id);

        @GetMapping("pms/brand/{id}")
        public ResponseVo<BrandEntity> queryBrandById(@PathVariable("id") Long id);

        @GetMapping("pms/spuattrvalue/spu/{spuId}")
        public ResponseVo<List<SpuAttrValueEntity>> querySearchAttrValueBySpuId(@PathVariable("spuId")Long spuId);

        @GetMapping("pms/skuattrvalue/sku/{skuId}")
        public ResponseVo<List<SkuAttrValueEntity>> querySearchAttrValueBySkuId(@PathVariable("skuId")Long skuId);

        /**
         * 根据sku中的spuId查询spu信息
         * @param id
         * @return
         */
        @GetMapping("pms/spu/{id}")
        @ApiOperation("详情查询")
        public ResponseVo<SpuEntity> querySpuById(@PathVariable("id") Long id);

        @GetMapping("pms/category/parent/{parentId}")
        public ResponseVo<List<CategoryEntity>> queryCategroy(@PathVariable("parentId") long pid);

        @GetMapping("pms/category/subs/{pid}")
        public ResponseVo<List<CategoryEntity>> queryCategoriesWithSub(@PathVariable("pid")Long pid);

        /**
         * 根据skuid查询sku信息
         * @param id
         * @return
         */
        @GetMapping("pms/sku/{id}")
        public ResponseVo<SkuEntity> querySkuById(@PathVariable("id") Long id);

        /**
         * 根据sku中spuId查询spu的描述信息
         * @param spuId
         * @return
         */
        @GetMapping("pms/spudesc/{spuId}")
        @ApiOperation("详情查询")
        public ResponseVo<SpuDescEntity> querySpuDescById(@PathVariable("spuId") Long spuId);

        /**
         * 根据三级分类id查询一二三级分类
         * @param cid3
         * @return
         */
        @GetMapping("pms/category/all/{cid3}")
        public ResponseVo<List<CategoryEntity>> queryCategoriesByCid3(@PathVariable("cid3")Long cid3);

        /**
         *  根据skuId查询sku的图片
         * @param skuId
         * @return
         */
        @GetMapping("pms/skuimages/sku/{skuId}")
        public ResponseVo<List<SkuImagesEntity>> queryImagesBySkuId(@PathVariable("skuId")Long skuId);

        /**
         * 根据spuId查询spu下的所有销售属性
         * @param spuId
         * @return
         */
        @GetMapping("pms/skuattrvalue/spu/{spuId}")
        public ResponseVo<List<SaleAttrValueVo>> querySkuAttrValuesBySpuId(@PathVariable("spuId")Long spuId);
        @GetMapping("pms/skuattrvalue/spu/sku/{spuId}")
        public ResponseVo<String> querySkusJsonBySpuId(@PathVariable("spuId") Long spuId);

        @GetMapping("pms/attrgroup/withattrvalues")
        public ResponseVo<List<ItemGroupVo>> queryGroupsBySpuIdAndCid(
                @RequestParam("spuId") Long spuId,
                @RequestParam("skuId") Long skuId,
                @RequestParam("cid") Long cid
        );

}
