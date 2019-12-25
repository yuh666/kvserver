package com.tmh.kvserver.httpserver.controller;

import com.tmh.kvserver.httpserver.Result;
import com.tmh.kvserver.raft.Raft;
import com.tmh.kvserver.raft.bean.AppendEntriesRequest;
import com.tmh.kvserver.raft.bean.VoteRequest;
import com.tmh.kvserver.utils.GsonUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/raft")
public class RaftClientController {


    @Autowired
    private Raft raft;

    @RequestMapping("/request-vote")
    @ResponseBody
    public Object reuqestForVote(String payload){
        VoteRequest request =  GsonUtils.fromJson(payload, VoteRequest.class);
        //TODO: 交给Raft处理
        return Result.success("");
    }


    @RequestMapping("/append-entries")
    @ResponseBody
    public Object appendEntries(String payload){
        AppendEntriesRequest request =  GsonUtils.fromJson(payload, AppendEntriesRequest.class);
        //TODO: 交给Raft处理
        return Result.success("");
    }
}