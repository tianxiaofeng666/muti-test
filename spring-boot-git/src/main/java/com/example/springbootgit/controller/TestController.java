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

    @RequestMapping("/test222")
    public String test222(){
        return "";
    }

    @RequestMapping("/test333")
    public String test333(){
        return "";
    }

    @RequestMapping("/test444")
    public String test444(){
        return "";
    }

}

