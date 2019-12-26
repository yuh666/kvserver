package com.tmh.kvserver.raft;

import com.tmh.kvserver.raft.bean.Command;
import com.tmh.kvserver.raft.bean.CommandTypeEnum;
import com.tmh.kvserver.raft.bean.LogEntry;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class HashMapStateMachine implements StateMachine {

    private HashMap<String, String> map = new HashMap<>();

    /**
     * 状态机添加和删除时加锁
     */
    private Lock lock = new ReentrantLock();

    @Override
    public void apply(LogEntry entry) {
        try {
            lock.tryLock(3000, TimeUnit.MILLISECONDS);
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
        } catch (InterruptedException e) {
            log.error("状态机执行获取锁线程被中断,异常信息:{}", e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String get(String key) {
        return map.get(key);
    }

}