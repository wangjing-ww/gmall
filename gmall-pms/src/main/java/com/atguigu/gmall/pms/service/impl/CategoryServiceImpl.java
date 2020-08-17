package com.atguigu.gmall.pms.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.CategoryMapper;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.service.CategoryService;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, CategoryEntity> implements CategoryService {

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<CategoryEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageResultVo(page);
    }

    @Autowired
    CategoryMapper categoryMapper;
    @Override
    public List<CategoryEntity> queryCategoriesWithSub(Long pid) {
        List<CategoryEntity> categoryEntityList = categoryMapper.queryCategoriesByPid(pid);
        return categoryEntityList;
    }

    @Override
    public List<CategoryEntity> queryCategoriesByCid3(Long cid3) {

        CategoryEntity categoryEntity3
                = categoryMapper.selectById(cid3);
        CategoryEntity categoryEntity2 = categoryMapper.selectById(categoryEntity3.getParentId());
        CategoryEntity categoryEntity1 = categoryMapper.selectById(categoryEntity2.getParentId());
        return Arrays.asList(categoryEntity1,categoryEntity2,categoryEntity3);
    }

}