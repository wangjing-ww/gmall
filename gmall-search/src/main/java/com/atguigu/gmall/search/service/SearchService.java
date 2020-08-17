package com.atguigu.gmall.search.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.feign.GmallPmsClient;
import com.atguigu.gmall.search.feign.GmallWmsClient;
import com.atguigu.gmall.search.pojo.*;
import com.atguigu.gmall.search.repository.GoodsRepository;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SearchService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    private ObjectMapper objectMapper;
    public SearchResponseVo search(SearchParamVo paramVo) {
        try {

            SearchRequest searchRequest = new SearchRequest(new String[]{"goods"}, bulidDsl(paramVo));
            SearchResponse searchResponse = this.restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            // 解析结果集
            SearchResponseVo responseVo = this.parseResult(searchResponse);
            return responseVo;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
    private SearchSourceBuilder bulidDsl(SearchParamVo paramVo){
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        String keyword = paramVo.getKeyword();
        if (StringUtils.isEmpty(keyword)){
            return null;
        }
        // 1构建查询条件

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // 1.1匹配查询（bool）
        boolQueryBuilder.must(QueryBuilders.matchQuery("title", keyword).operator(Operator.AND));
        //1.2 过滤
        //1.2.1 品牌过滤

        List<Long> brandId = paramVo.getBrandId();
        if (!CollectionUtils.isEmpty(brandId)){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId", brandId));
        }
        // 1.2.2 分类过滤
        Long cid = paramVo.getCid();
        if (cid!=null){
            boolQueryBuilder.filter(QueryBuilders.termQuery("categoryId", cid));
        }
        //1.2.3 价格区间过滤
        Integer priceFrom = paramVo.getPriceFrom();
        Integer priceTo = paramVo.getPriceTo();
        if (priceFrom!=null||priceTo!=null){
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("price");
            if (priceFrom!=null){
                rangeQuery.gte(priceFrom);
            }
            if (priceTo!=null){
                rangeQuery.lte(priceTo);
            }
            boolQueryBuilder.filter(rangeQuery);
        }
        //1.2.4 是否有货
        Boolean store = paramVo.getStore();
        if (store!=null){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("store",store));
        }
        //1.2.5 规格参数过滤
        List<String> props = paramVo.getProps();
        if (!CollectionUtils.isEmpty(props)){
            props.forEach(prop -> {
                String[] attrs = StringUtils.split(prop, ":");
                if (attrs!=null&&attrs.length==2){
                    String attrId = attrs[0];
                    String attrValueString = attrs[1];
                    String[] attrValues = StringUtils.split(attrValueString, "-");
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    boolQuery.must(QueryBuilders.termQuery("searchAttrs.attrId", attrId));
                    boolQuery.must(QueryBuilders.termQuery("searchAttrs.attrValue", attrValues));
                    boolQueryBuilder.filter(QueryBuilders.nestedQuery("searchAttrs", boolQuery, ScoreMode.None));
                }
            });
        }
        sourceBuilder.query(boolQueryBuilder);
        // 2 构建排序
        Integer sort = paramVo.getSort();

        switch (sort){
            // 1-按价格升序；
            case 1: sourceBuilder.sort("price", SortOrder.ASC); break;
            // 2-按价格降序；
            case 2: sourceBuilder.sort("price", SortOrder.DESC);break;
            // 3-按创建时间降序；
            case 3: sourceBuilder.sort("createTime", SortOrder.DESC); break;
            // 4-按销量降序
            case 4: sourceBuilder.sort("sales", SortOrder.DESC); break;
        }
        // 3构建分页
        Integer pageNum = paramVo.getPageNum();
        Integer pageSize = paramVo.getPageSize();
        sourceBuilder.from((pageNum-1)*pageSize);
        sourceBuilder.size(pageSize);
        //4 构建高亮
        sourceBuilder.highlighter(new HighlightBuilder().field("title").preTags("<font style='color:red'>").postTags("</font>"));
        //5 构建聚合
        // 5.1 构建品牌聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("brandIdAgg").field("brandId")
                     .subAggregation(AggregationBuilders.terms("brandNameAgg").field("brandName"))
                     .subAggregation(AggregationBuilders.terms("logoAgg").field("logo")));
        // 5.2 构建分类聚合

        sourceBuilder.aggregation(AggregationBuilders.terms("categoryIdAgg").field("categoryId")
                     .subAggregation(AggregationBuilders.terms("categoryNameAgg").field("categoryName")));
        // 5.3 构建规格参数聚合
        sourceBuilder.aggregation(AggregationBuilders.nested("attrAgg", "searchAttrs")
        .subAggregation(AggregationBuilders.terms("attrIdAgg").field("searchAttrs.attrId")
        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("searchAttrs.attrName"))
        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("searchAttrs.attrValue"))));

        // 6构建结果集过滤
        sourceBuilder.fetchSource(new String[]{"skuId","title","price","defaultImage"}, null);
        System.out.println(sourceBuilder.toString());
        return sourceBuilder;
    }

    private SearchResponseVo parseResult(SearchResponse searchResponse){
        SearchResponseVo responseVo = new SearchResponseVo();
        // 总记录数
        SearchHits hits = searchResponse.getHits();
        responseVo.setTotal(hits.totalHits);

        SearchHit[] hitsHits = hits.getHits();
        List<Goods> goodsList = Stream.of(hitsHits).map(hitsHit -> {
            // 获取内存hits的_sourse 数据
            String goodsJson = hitsHit.getSourceAsString();
            //反序列化为goods对象
            Goods goods = JSON.parseObject(goodsJson, Goods.class);
            // 获取高亮的title覆盖普通title
            Map<String, HighlightField> highlightFields = hitsHit.getHighlightFields();
            HighlightField highlightField = highlightFields.get("title");
            String highlightTitle = highlightField.getFragments()[0].toString();
            goods.setTitle(highlightTitle);
            return goods;
        }).collect(Collectors.toList());
        responseVo.setGoodsList(goodsList);
        // 聚合结果集的解析
        Map<String, Aggregation> aggregationMap = searchResponse.getAggregations().asMap();
        // 解析聚合结果，获取品牌
        ParsedLongTerms brandIdAgg = (ParsedLongTerms)aggregationMap.get("brandIdAgg");
        List<? extends Terms.Bucket> buckets = brandIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(buckets)){
            List<BrandEntity> brandEntities = buckets.stream().map(bucket -> {
                BrandEntity brandEntity = new BrandEntity();
                // 聚合品牌
                long brandId = bucket.getKeyAsNumber().longValue();
                brandEntity.setId(brandId);
                // 解析品牌的子聚合 获取品牌名称
                Map<String, Aggregation> subAggregationMap = bucket.getAggregations().asMap();
                ParsedStringTerms brandNameAgg = (ParsedStringTerms)subAggregationMap.get("brandNameAgg");
                brandEntity.setName(brandNameAgg.getBuckets().get(0).getKeyAsString());
                // 解析品牌子聚合 获取品牌logo
                ParsedStringTerms logoAgg = (ParsedStringTerms)subAggregationMap.get("logoAgg");
                List<? extends Terms.Bucket> logoAggBuckets = logoAgg.getBuckets();
                if (!CollectionUtils.isEmpty(logoAggBuckets)){
                    String logo = logoAggBuckets.get(0).getKeyAsString();
                    brandEntity.setLogo(logo);
                }
                return brandEntity;
            }).collect(Collectors.toList());
            responseVo.setBrands(brandEntities);
        }
           // 2 解析结果集 获取分类
        ParsedLongTerms categoryIdAgg = (ParsedLongTerms)aggregationMap.get("categoryIdAgg");
        List<? extends Terms.Bucket> categoryIdAggBuckets = categoryIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(categoryIdAggBuckets)){
            List<CategoryEntity> categoryEntities = categoryIdAggBuckets.stream().map(bucket -> {
                CategoryEntity categoryEntity = new CategoryEntity();
                long categoryId = bucket.getKeyAsNumber().longValue();
                categoryEntity.setId(categoryId);
                ParsedStringTerms categoryNameAgg = (ParsedStringTerms) bucket.getAggregations().get("categoryNameAgg");
                categoryEntity.setName(categoryNameAgg.getBuckets().get(0).getKeyAsString());
                return categoryEntity;
            }).collect(Collectors.toList());
            responseVo.setCategories(categoryEntities);

            // 解析结果集 获取规格参数
            ParsedNested attrAgg = (ParsedNested)aggregationMap.get("attrAgg");
            ParsedLongTerms attrIdAgg = (ParsedLongTerms)attrAgg.getAggregations().get("attrIdAgg");
            List<? extends Terms.Bucket> attrIdAggBuckets = attrIdAgg.getBuckets();
            if (!CollectionUtils.isEmpty(attrIdAggBuckets)){
                List<SearchResponseAttrVo> searchResponseAttrVos = attrIdAggBuckets.stream().map(bucket -> {
                    SearchResponseAttrVo responseAttrVo = new SearchResponseAttrVo();
                    responseAttrVo.setAttrId(bucket.getKeyAsNumber().longValue());
                    // 规格参数name
                    ParsedStringTerms attrNameAgg = (ParsedStringTerms) bucket.getAggregations().get("attrNameAgg");
                    responseAttrVo.setAttrName(attrNameAgg.getBuckets().get(0).getKeyAsString());
                    // 设置规格参数值
                    ParsedStringTerms attrValueAgg = (ParsedStringTerms) bucket.getAggregations().get("attrValueAgg");
                    List<? extends Terms.Bucket> attrValueAggBuckets = attrValueAgg.getBuckets();
                    if (!CollectionUtils.isEmpty(attrValueAggBuckets)) {
                        List<String> attrValues = attrValueAggBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                        responseAttrVo.setAttrValues(attrValues);
                    }
                    return responseAttrVo;
                }).collect(Collectors.toList());
                responseVo.setFilters(searchResponseAttrVos);
            }
        }
        return responseVo;
    }

    @Autowired
    GmallPmsClient pmsClient;
    @Autowired
    GmallWmsClient wmsClient;
    @Autowired
    GoodsRepository goodsRepository;

    public void createIndex(Long spuId) {
        ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(spuId);
        SpuEntity spuEntity = spuEntityResponseVo.getData();

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
        }
}

