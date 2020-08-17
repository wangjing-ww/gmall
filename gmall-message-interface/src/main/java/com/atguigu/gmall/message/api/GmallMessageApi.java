package com.atguigu.gmall.message.api;

import com.atguigu.gmall.common.bean.ResponseVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

public interface GmallMessageApi {
    @GetMapping("message/send/{mobile}")
    public ResponseVo<Object> getCode(@PathVariable String mobile);
}
