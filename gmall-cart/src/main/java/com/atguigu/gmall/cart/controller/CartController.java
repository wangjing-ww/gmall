package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.bean.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import javax.websocket.server.PathParam;
import java.util.List;


@Controller
public class CartController {

    @Autowired
     CartService cartService;

    @GetMapping("check/{userId")
    @ResponseBody
    public ResponseVo<List<Cart>> queryCheckedCarts(@PathVariable("userId")Long userId){
        List<Cart> carts =  cartService.queryCheckedCarts(userId);
        return ResponseVo.ok(carts);
    }

   @PostMapping("deleteCart")
   @ResponseBody
   public ResponseVo<Object> deleteCart(@RequestParam("skuId")Long skuId){

       cartService.deleteCart(skuId);
       return ResponseVo.ok();

   }
    @PostMapping("updateNum")
    @ResponseBody
    public ResponseVo<Object> updateNum(@RequestBody Cart cart){
        cartService.updateNum(cart);
        return ResponseVo.ok();
    }
    @GetMapping("cart.html")
    public String queryCarts(Model model){
      List<Cart> carts =  cartService.queryCarts();
      model.addAttribute("carts", carts);
      return "cart";

    }
    @GetMapping
    public String addCart(Cart cart){
          if (cart==null||cart.getSkuId() ==null){
              throw  new RuntimeException("该购物车不存在 快去添加商品吧!!!");
          }
        cartService.addCart(cart);

        return "redirect:http://cart.gmall.com/addCart.html?skuId=" + cart.getSkuId();
    }

    @GetMapping("addCart.html")
    public String queryCartBySkuId(@PathParam("skuId")Long skuId, Model model){
       Cart cart =  cartService.queryCartBySkuId(skuId);
       model.addAttribute("cart", cart);
        return "addCart";
    }

    @GetMapping("test")
    @ResponseBody
    public String test(){
       /* UserInfo userInfo = LoginInterceptor.getUserInfo();
        System.out.println("userInfo = " + userInfo);*/
        long start = System.currentTimeMillis();
         cartService.executor1();
         cartService.executor2();
      /*  cartService.executor3().addCallback(new SuccessCallback<String>() {

            @Override
            public void onSuccess(String result) {
                System.out.println("正常情况下 返回结果： ");
                System.out.println("result = " + result);
            }
        }, new FailureCallback() {
            @Override
            public void onFailure(Throwable throwable) {
                System.out.println("异常情况下 返回结果：");
                System.out.println("throwable = " + throwable.getMessage());
            }
        });*/

      /*      cartService.executor4().addCallback(new SuccessCallback<String>() {
            @Override
            public void onSuccess(String result) {
                System.out.println("正常情况下 返回结果： ");
                System.out.println("result = " + result);
            }
        }, new FailureCallback() {
            @Override
            public void onFailure(Throwable ex) {
                System.out.println("异常情况下 返回结果：");
                System.out.println("throwable = " + ex.getMessage());
            }
        });*/
      /*  try {
            Future<String> executor3 = cartService.executor3();
            System.out.println("executor3.get() = " + executor3.get());
            Future<String> executor4 = cartService.executor4();
            System.out.println("executor4.get() = " + executor4.get());
        } catch (Exception e) {
            e.printStackTrace();
        } */
        System.out.println("执行完成，     耗时："+(System.currentTimeMillis()-start));
        return "hello cart!!" ;
    }
}
