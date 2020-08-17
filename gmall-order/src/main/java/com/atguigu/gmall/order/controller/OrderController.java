package com.atguigu.gmall.order.controller;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.oms.entity.OrderSubmitVo;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.order.vo.OrderConfirmVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("confirm")
    @ResponseBody
    public ResponseVo<OrderConfirmVo> confirm(){
        OrderConfirmVo confirmVo = this.orderService.confirm();
      return ResponseVo.ok(confirmVo);
    }

    @PostMapping("submit")
    @ResponseBody
    public ResponseVo<OrderEntity> submit(@RequestBody OrderSubmitVo submitVo){
        OrderEntity orderEntity = orderService.submit(submitVo);
        return ResponseVo.ok(orderEntity);
    }


}
