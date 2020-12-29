package com.example.springbootgit.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @RequestMapping("/test")
    public String test(){
        return "进来了。";
    }

    @RequestMapping("/test111")
    public String test111(){
        return "";
    }
}

