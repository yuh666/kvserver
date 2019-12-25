package com.tmh.kvserver.raft;

import com.tmh.kvserver.raft.bean.AppendEntriesRequest;
import com.tmh.kvserver.raft.bean.AppendEntriesResponse;
import com.tmh.kvserver.raft.bean.VoteRequest;
import com.tmh.kvserver.raft.bean.VoteResponse;
import lombok.Data;
import lombok.ToString;

@Data
public class Peer {

    private String host; // 节点 host
    private int    port; // 节点 port
    private int    peerId; // 节点 id


    public Peer(String host, int port, int peerId) {
        this.host = host;
        this.port = port;
        this.peerId = peerId;
    }

    public VoteResponse requestVote(VoteRequest request) {
        //TODO: http 
        return null;
    }

    public AppendEntriesResponse sendAppend(AppendEntriesRequest request) {
        //TODO: http 
        return null;
    }

}