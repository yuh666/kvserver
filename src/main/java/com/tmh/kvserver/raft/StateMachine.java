package com.tmh.kvserver.raft;

import com.tmh.kvserver.raft.bean.LogEntry;

/**
 * 状态机  
 */
public interface StateMachine {

    void apply(LogEntry entry);
    String get(String key);

}