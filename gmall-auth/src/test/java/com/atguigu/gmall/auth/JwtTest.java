package com.atguigu.gmall.auth;

import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.common.utils.RsaUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class JwtTest {

    // 别忘了创建D:\\project\rsa目录
    private static final String pubKeyPath = "D:\\learnplace\\rsa\\rsa.pub";
    private static final String priKeyPath = "D:\\learnplace\\rsa\\rsa.pri";

    private PublicKey publicKey;

    private PrivateKey privateKey;

    @Test
    public void testRsa() throws Exception {
        RsaUtils.generateKey(pubKeyPath, priKeyPath, "aa234BB");
    }

       @BeforeEach
    public void testGetRsa() throws Exception {
        this.publicKey = RsaUtils.getPublicKey(pubKeyPath);
        this.privateKey = RsaUtils.getPrivateKey(priKeyPath);
    }

    @Test
    public void testGenerateToken() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "11");
        map.put("username", "liuyan");
        // 生成token
        String token = JwtUtils.generateToken(map, privateKey, 5);
        System.out.println("token = " + token);
    }

    @Test
    public void testParseToken() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJpZCI6IjExIiwidXNlcm5hbWUiOiJsaXV5YW4iLCJleHAiOjE1OTY1MjYwMDJ9.LGzeUAVidntXuQDrJke4H1gbwUGq8z8rP9PRmFaKH7QlisOlpFakuWLDHNZNZuopPeuo5cYLVaJS5FomLhRq_b9f3L2uNUTA-hB0rVrZdeXml7Un07wcGd9fG6Hk590hznwf-FpVeTyDvuFOIvuBL5J02xIyChQ4A5N61JXWQInzVqGEsfQrun3H_fynz9cQgnXb0iKlhPVNIrIiOISL9MpSy7h_ZLiJyv5jXJ13hC_LqBcXzI9PdNoXt8uYMkd4lYR8mmWN0ZPVcfB8EVzdL2qPz-di3diTPNEgL5N_mkKPCankrF_r2ce8SQV8_pZ-NZSrrTl83IWooeUtS0bzBQ";

        // 解析token
        Map<String, Object> map = JwtUtils.getInfoFromToken(token, publicKey);
        System.out.println("id: " + map.get("id"));
        System.out.println("userName: " + map.get("username"));
    }
}
