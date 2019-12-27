package com.tmh.kvserver.raft;

import com.tmh.kvserver.raft.bean.AppendEntriesRequest;
import com.tmh.kvserver.raft.bean.AppendEntriesResponse;
import com.tmh.kvserver.raft.bean.VoteRequest;
import com.tmh.kvserver.raft.bean.VoteResponse;
import com.tmh.kvserver.utils.GsonUtils;
import com.tmh.kvserver.utils.HttpClientUtil;

import lombok.Data;
import lombok.ToString;
import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class Peer {

    private String host; // 节点 host
    private int port; // 节点 port
    private int peerId; // 节点 id

    public Peer(String host, int port, int peerId) {
        this.host = host;
        this.port = port;
        this.peerId = peerId;
    }

    public VoteResponse requestVote(VoteRequest request) {
        String requestParam = GsonUtils.toJson(request);
        log.info("requestVote: {} => {} : {}", request.getCandidateId(), this.peerId, requestParam);
        String resultStr = HttpClientUtil.restPost(getBasePath() + "/raft/request-vote", requestParam);
        log.info("requestVoteReply: {} => {} : {}", this.peerId,request.getCandidateId(), resultStr);
        return GsonUtils.fromJson(resultStr, VoteResponse.class);
    }

    public AppendEntriesResponse sendAppend(AppendEntriesRequest request) {
        // TODO: http
        return null;
    }

    private String getBasePath() {
        return "http://" + host + ":" + port;
    }

}