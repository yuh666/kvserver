package com.tmh.kvserver.raft.bean;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VoteRequest{

    private int candidateId; //候选者Id 
    private int term; // cand term
    private int lastTerm; // 最新entry的term
    private int lastIndex; // 最新entry的index
    

}