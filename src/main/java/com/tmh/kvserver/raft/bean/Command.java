package com.tmh.kvserver.raft.bean;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Command {
    
    private int commandType; // command的类型 
    private String key; // key
    private String val; // val
    
}