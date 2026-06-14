package com.learn.demo.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpringController {


    @GetMapping("/validation")
    public String startMethod() {
        return "I am an external API";
    }


}
