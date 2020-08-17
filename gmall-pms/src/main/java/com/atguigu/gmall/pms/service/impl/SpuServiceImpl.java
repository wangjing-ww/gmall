package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.feign.GmallSmsClient;
import com.atguigu.gmall.pms.mapper.SkuMapper;
import com.atguigu.gmall.pms.mapper.SpuDescMapper;
import com.atguigu.gmall.pms.service.*;
import com.atguigu.gmall.pms.vo.BaseAttrValueVo;
import com.atguigu.gmall.pms.vo.SkuVo;
import com.atguigu.gmall.pms.vo.SpuVo;
import com.atguigu.gmall.sms.vo.SkuSaleVo;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.SpuMapper;
import org.springframework.util.CollectionUtils;


@Slf4j
@Service("spuService")
public class SpuServiceImpl extends ServiceImpl<SpuMapper, SpuEntity> implements SpuService {

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SpuEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public PageResultVo querySpuInfo(PageParamVo pageParamVo, Long categroyId) {
        QueryWrapper<SpuEntity> queryWrapper = new QueryWrapper<>();
        if (categroyId != 0){
            queryWrapper.eq("category_id", categroyId);
        }
        String key = pageParamVo.getKey();
        if (StringUtils.isNotBlank(key)){
            queryWrapper.and( t->{
                t.like("name",key).or().like("id", key);
            });
        }
        IPage<SpuEntity> page = this.page(
                pageParamVo.getPage(),
                 queryWrapper
        );

        return new PageResultVo(page);
    }
   /*
     保存
    */
   @Autowired
    SpuDescMapper spuDescMapper;

   @Autowired
    SpuAttrValueService spuAttrValueService;

   @Autowired
    SkuMapper skuMapper;
   @Autowired
    SkuImagesService skuImagesService;
   @Autowired
    SkuAttrValueService skuAttrValueService;

   @Autowired
    GmallSmsClient gmallSmsClient;

   @Autowired
    RabbitTemplate rabbitTemplate;
//    private void sendMessage(Long id, String type){
//        // 发送消息
//        try {
//            this.rabbitTemplate.convertAndSend("item_exchange", "item." + type, id);
//        } catch (Exception e) {
//            log.error("{}商品消息发送异常，商品id：{}", type, id, e);
//        }
//    }
    //@GlobalTransactional
    @Override
    public void bigSave(SpuVo spuVo) {
        // 1 保存spu信息
        // 1.1 保存spu基本信息
        spuVo.setPublishStatus(1);
        spuVo.setCreateTime(new Date());
        spuVo.setUpdateTime(spuVo.getCreateTime());
        this.save(spuVo);
        Long spuId = spuVo.getId();
        // 1.2 写入 属性描述
        SpuDescEntity spuDescEntity = new SpuDescEntity();
        spuDescEntity.setSpuId(spuId);
        spuDescEntity.setDecript(StringUtils.join(spuVo.getSpuImages(), ","));
        spuDescMapper.insert(spuDescEntity);
        //1.3 保存baseAttrs
        List<BaseAttrValueVo> baseAttrs = spuVo.getBaseAttrs();
        if (!CollectionUtils.isEmpty(baseAttrs)){
            List<SpuAttrValueEntity> spuAttrValueEntities = baseAttrs.stream().map(spuAttrValueVo -> {
                spuAttrValueVo.setSpuId(spuId);
                spuAttrValueVo.setSort(0);
                return spuAttrValueVo;
            }).collect(Collectors.toList());
           this.spuAttrValueService.saveBatch(spuAttrValueEntities);
        }

        /// 2. 保存sku相关信息
        // 2.1. 保存sku基本信息
        List<SkuVo> skus = spuVo.getSkus();
        if (CollectionUtils.isEmpty(skus)){
            return;
        }
        skus.forEach(skuVo -> {
            SkuEntity skuEntity = new SkuEntity();
            BeanUtils.copyProperties(skuVo, skuEntity);
            skuEntity.setCatagoryId(spuVo.getCategoryId());
            skuEntity.setBrandId(spuVo.getBrandId());
            // 获取图片列表信息
            List<String> images = skuVo.getImages();
            // 判断图片列表是否为空
            if (!CollectionUtils.isEmpty(images)){
                // 设置默认图片，如果没有设置默认图片 则默认第一张图片 为 默认图片
                skuEntity.setDefaultImage(skuEntity.getDefaultImage() == null ? images.get(0) :skuEntity.getDefaultImage());
            }
            skuEntity.setSpuId(spuId);
            // 将 sku信息插入sku表中
            this.skuMapper.insert(skuEntity);
            // 获取 skuId
            Long skuId = skuEntity.getId();

            // 2.2. 保存sku图片信息
            if (!CollectionUtils.isEmpty(images)){
                String defaultImage = images.get(0);
                List<SkuImagesEntity> skuImageses = images.stream().map(image -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setDefaultStatus(StringUtils.equals(defaultImage, image) ? 1 : 0);
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setSort(0);
                    skuImagesEntity.setUrl(image);
                    return skuImagesEntity;
                }).collect(Collectors.toList());
                this.skuImagesService.saveBatch(skuImageses);
            }
            // 2.3. 保存sku的规格参数（销售属性）
            List<SkuAttrValueEntity> saleAttrs = skuVo.getSaleAttrs();
            saleAttrs.forEach(skuAttrValueEntity -> {
                skuAttrValueEntity.setSkuId(skuId);
                skuAttrValueEntity.setSort(0);
            });
            this.skuAttrValueService.saveBatch(saleAttrs);
            // 3. 保存营销相关信息，需要远程调用gmall-sms
            SkuSaleVo skuSaleVo = new SkuSaleVo();
            BeanUtils.copyProperties(skuVo,skuSaleVo);
            skuSaleVo.setSkuId(skuId);
            this.gmallSmsClient.saveSkuSaleInfo(skuSaleVo);
        });
        this.rabbitTemplate.convertAndSend("PMS-ITEM-EXCHANGE", "item.insert",spuId);

    }



}