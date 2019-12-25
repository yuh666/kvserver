package com.tmh.kvserver.httpserver.controller;

import com.tmh.kvserver.httpserver.Result;
import com.tmh.kvserver.raft.Raft;
import com.tmh.kvserver.raft.StateMachine;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@Controller
@RequestMapping("/cli")
public class KvClientController {


    @Autowired
    private Raft raft;

    @Autowired
    private StateMachine stateMachine;

    @RequestMapping("/get")
    @ResponseBody
    public Object get(String key) {
        //TODO: 1.判断是否重定向  2.查询     
        return Result.success("");
    }


    @RequestMapping("/put")
    @ResponseBody
    public Object put(String key,String val) {
        //TODO: 1.判断是否重定向  2.处理    
        return Result.success("");
    }

}