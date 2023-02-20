package com.example.springbootgit.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @RequestMapping("/getUser")
    public String getUser(){
        return "用户不存在！!!!!!@@@";
    }

    @RequestMapping("/addUser")
    public String addUser(){
        /**
         * 修改生产bug3333444-dev
         */
        String a = "aa";
        String b = "bb";
        String d = "dd";
        String c = "cc";
        return "添加成功！bug修复！444";
    }

    @RequestMapping("/deleteUser")
    public String deleteUser(){
        return "删除成功！444";
    }
}
