package com.tmh.kvserver.raft.bean;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogEntry {
    private int index; // entry的index 
    private int term; // entry的term
    private Command command; // entry内部的command
}