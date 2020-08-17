package com.atguigu.gmall.ums.feign;

import com.atguigu.gmall.message.api.GmallMessageApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient("message-service")
public interface GmallMessageClient extends GmallMessageApi {
}
