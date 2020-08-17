package com.atguigu.gmall.pms.vo;

import com.atguigu.gmall.pms.vo.AttrValueVo;
import lombok.Data;

import java.util.List;

@Data
public class ItemGroupVo {

    private String groupName;
    private Long groupId;
    private List<AttrValueVo> attrValues;
}
