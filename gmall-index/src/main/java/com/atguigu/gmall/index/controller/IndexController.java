package com.atguigu.gmall.index.controller;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.service.IndexService;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class IndexController {

    @Autowired
    private IndexService indexService;
    @ResponseBody
    @GetMapping("index/write")
    public ResponseVo<String> testWrite(){

       String msg =  indexService.testWrite();

        return ResponseVo.ok(msg);
    }
    @ResponseBody
    @GetMapping("index/read")
    public ResponseVo<String> testRead(){

        String msg =  indexService.testRead();

        return ResponseVo.ok(msg);
    }
    @ResponseBody
    @GetMapping("index/testLock")
    public ResponseVo<Object> testLock(){

        indexService.testLock();

        return ResponseVo.ok(null);
    }
    @ResponseBody
    @GetMapping("index/cates/{pid}")
    public ResponseVo<List<CategoryEntity>> queryLevelTwoCategoriesWithSub(@PathVariable("pid")Long pid){
        List<CategoryEntity> categoryEntities =  indexService.queryLevelTwoCategoriesWithSub(pid);
         return ResponseVo.ok(categoryEntities);
    }
    @GetMapping
    public String toIndex(Model model){
        List<CategoryEntity> categoryEntities =  indexService.queryLevelOneCategories();
         model.addAttribute("categories", categoryEntities);
        return "index";

    }
}
