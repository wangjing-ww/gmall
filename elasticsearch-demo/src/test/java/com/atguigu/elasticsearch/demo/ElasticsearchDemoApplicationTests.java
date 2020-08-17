package com.atguigu.elasticsearch.demo;

import com.atguigu.elasticsearch.demo.api.UserRepository;
import com.atguigu.elasticsearch.demo.pojo.User;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SpringBootTest
class ElasticsearchDemoApplicationTests {
    @Autowired
    ElasticsearchRestTemplate restTemplate;
    @Test
    void contextLoads() {
        // 创建索引库
     this.restTemplate.createIndex(User.class);
     // 建立映射关系
     this.restTemplate.putMapping(User.class);
     //删除索引库
    // this.restTemplate.deleteIndex(User.class);
    }

    @Autowired
    UserRepository userRepository;

    @Test
    void test(){
        System.out.println(userRepository);
    }
    @Test
    void add(){
        this.userRepository.save(new User(1l,"jingjing",18,"123456"));
    }
    @Test
    void delete(){
        userRepository.deleteById(1l);
    }
    @Test
    void testFind(){
        Optional<User> user = this.userRepository.findById(1l);
        System.out.println(user);
        User user1 = user.get();
        System.out.println("user1 = " + user1);
    }

    @Test
    void testAddAll(){
        List<User> users = new ArrayList<>();
        users.add(new User(1l, "柳岩", 18, "123456"));
        users.add(new User(2l, "范冰冰", 19, "123456"));
        users.add(new User(3l, "李冰冰", 20, "123456"));
        users.add(new User(4l, "锋哥", 21, "123456"));
        users.add(new User(5l, "小鹿", 22, "123456"));
        users.add(new User(6l, "韩红", 23, "123456"));
        this.userRepository.saveAll(users);
    }
    @Test
    void testFindByAgeBetween(){
        List<User> users = userRepository.findByAgeBetween(18, 20);
        users.forEach(System.out::println);
    }

    @Test
    void testNative(){
        // 初始化 查询对象
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
        // 构建查询
        queryBuilder.withQuery(QueryBuilders.matchQuery("name", "冰冰"));
         // sort
        queryBuilder.withSort(SortBuilders.fieldSort("age").order(SortOrder.DESC));
        //分页、
        queryBuilder.withPageable(PageRequest.of(0, 2));
        // 高亮
     //   queryBuilder.withHighlightBuilder(new HighlightBuilder().field("name").preTags("<em>").postTags("</em>"));
        Page<User> users = userRepository.search(queryBuilder.build());
       // System.out.println("users = " + users);
        users.get().forEach(System.out::println);
        users.getContent().forEach(System.out::println);
        System.out.println("users.getTotalElements() = " + users.getTotalElements());
        System.out.println("users.getTotalPages() = " + users.getTotalPages());


    }

}
