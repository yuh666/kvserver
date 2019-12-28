package com.tmh.kvserver.raft.bean;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppendEntriesRequest {
    private int            term; // leader term
    private int            leaderId; //leaderId
    private int            prevLogIndex; // prevLogIndex
    private int            prevLogTerm; // prevLogIndex 条目的任期号
    private List<LogEntry> entries; // entries
    private int            leaderCommit;  // leader的提交索引
}