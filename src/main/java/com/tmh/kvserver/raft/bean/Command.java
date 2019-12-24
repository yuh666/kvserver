package com.tmh.kvserver.raft.bean;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Command {
    
    private int commandType; // command的类型 
    private String data; // command的数据
    
}