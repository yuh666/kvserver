package com.tmh.kvserver.raft;

import java.util.HashMap;

import com.tmh.kvserver.raft.bean.Command;
import com.tmh.kvserver.raft.bean.CommandTypeEnum;
import com.tmh.kvserver.raft.bean.LogEntry;

public class HashMapStateMachine implements StateMachine {

    private HashMap<String, String> map = new HashMap<>();

    @Override
    public void apply(LogEntry entry) {
        if (entry == null || entry.getCommand() == null
                || entry.getCommand().getCommandType() == CommandTypeEnum.Noop.getCode()) {
            return;
        }
        Command cmd = entry.getCommand();
        int type = cmd.getCommandType();
        if (type == CommandTypeEnum.Set.getCode()) {
            map.put(cmd.getKey(), cmd.getVal());
        } else if (type == CommandTypeEnum.Remove.getCode()) {
            map.remove(cmd.getKey());
        }
    }

    @Override
    public String get(String key) {
        return map.get(key);
    }

}