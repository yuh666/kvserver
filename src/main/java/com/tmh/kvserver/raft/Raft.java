package com.tmh.kvserver.raft;

import java.util.ArrayList;
import java.util.List;

import com.tmh.kvserver.raft.bean.LogEntry;

/**
 * Raft实现
 */
public class Raft {

    // Persistent state on all servers
    private int currentTerm; //当前term
    private int votedFor; // 给谁投票
    private List<LogEntry> log = new ArrayList<>();
    private StateMachine stateMachine; // 状态机

    //Volitale state on all servers
    private int commitIndex; //提交位置
    private int lastApplied;//最后应用的位置
    
    //Volitale state on leader
    private int[] nextIndex;
    private int[] matchIndex;


}