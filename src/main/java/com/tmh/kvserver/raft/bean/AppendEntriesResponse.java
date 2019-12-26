package com.tmh.kvserver.raft.bean;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AppendEntriesResponse {
    private int     term; // flw term
    private boolean success; // 是否成功接收
}