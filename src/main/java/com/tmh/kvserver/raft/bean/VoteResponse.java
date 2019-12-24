package com.tmh.kvserver.raft.bean;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VoteResponse{

    private int term; // flw term
    private boolean voteGranted; // 是否成功

}