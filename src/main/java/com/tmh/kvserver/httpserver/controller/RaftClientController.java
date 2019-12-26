package com.tmh.kvserver.httpserver.controller;

import com.tmh.kvserver.httpserver.Result;
import com.tmh.kvserver.raft.Raft;
import com.tmh.kvserver.raft.bean.AppendEntriesRequest;
import com.tmh.kvserver.raft.bean.AppendEntriesResponse;
import com.tmh.kvserver.raft.bean.VoteRequest;
import com.tmh.kvserver.utils.GsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/raft")
@Slf4j
public class RaftClientController {


    @Autowired
    private Raft raft;

    @RequestMapping("/request-vote")
    @ResponseBody
    public Result requestForVote(@RequestBody VoteRequest request) {
        //TODO: 交给Raft处理
        return Result.success(raft.requestForVote(request));
    }


    @RequestMapping("/append-entries")
    @ResponseBody
    public Result appendEntries(@RequestBody AppendEntriesRequest request) {
        AppendEntriesResponse response = raft.appendEntries(request);
        //TODO: 交给Raft处理
        return Result.success(response);
    }
}