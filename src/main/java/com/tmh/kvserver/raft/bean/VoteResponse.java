package com.tmh.kvserver.raft.bean;


import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VoteResponse {

    private int     term; // flw term
    private boolean voteGranted; // 是否成功

}