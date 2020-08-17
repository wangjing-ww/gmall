package com.atguigu.gmall.search.pojo;

import lombok.Data;

import java.util.List;

/**
 * 接受页面传递过来的检索参数
 * search?keyword=小米&brandId=1,3&cid=225&props=5:高通-麒麟&props=6:骁龙865-硅谷1000&sort=1&priceFrom=1000&priceTo=6000&pageNum=1&store=true
 *
 */

@Data
public class SearchParamVo {
        private String keyword; //检索条件
        private List<Long> brandId; //品牌过滤
        private Long cid; // 类别过滤
        private List<String> props; //过滤的检索参数
       // 排序字段：0-默认，得分降序；1-按价格升序；2-按价格降序；3-按创建时间降序；4-按销量降序
       private Integer sort = 0;

       // 价格区间
       private Integer priceFrom;
       private Integer priceTo;

       private Integer pageNum = 1;//页码
       private  Integer pageSize = 5; // 每页记录数

        private Boolean store;  //是否有货




}
