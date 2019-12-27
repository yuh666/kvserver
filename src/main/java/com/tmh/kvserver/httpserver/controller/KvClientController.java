package com.tmh.kvserver.httpserver.controller;

import com.tmh.kvserver.httpserver.Result;
import com.tmh.kvserver.raft.Raft;
import com.tmh.kvserver.raft.StateMachine;
import com.tmh.kvserver.raft.bean.Command;
import com.tmh.kvserver.raft.bean.CommandTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/cli")
public class KvClientController {


    @Autowired
    private Raft raft;

    @Autowired
    private StateMachine stateMachine;

    @RequestMapping("/get")
    @ResponseBody
    public Result get(@RequestBody Command command) {
        //TODO: 1.判断是否重定向  2.查询
        command.setCommandType(CommandTypeEnum.GET.getCode());
        return raft.handlerClientRequest(command);
    }


    @RequestMapping("/put")
    @ResponseBody
    public Result put(@RequestBody Command command) {
        //TODO: 1.判断是否重定向  2.处理
        command.setCommandType(CommandTypeEnum.Set.getCode());
        return raft.handlerClientRequest(command);
    }

    @RequestMapping("/remove")
    @ResponseBody
    public Result remove(@RequestBody Command command) {
        //TODO: 1.判断是否重定向  2.处理
        command.setCommandType(CommandTypeEnum.Remove.getCode());
        return raft.handlerClientRequest(command);
    }

}