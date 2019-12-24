package com.tmh.kvserver.raft.bean;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppendEntriesRequest {
    private int term; // leader term
    private int leaderId; //leaderId
    private int prevLogIndex; // prevLogIndex
    private List<LogEntry> entries; // entries
    private int leaderCommit;  // leader的提交索引
}