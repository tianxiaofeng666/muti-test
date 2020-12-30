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
        String a = "aa";
        String b = "bb";
        String d = "dd";
        return "添加成功！";
    }
}
