package com.tmh.kvserver.raft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.tmh.kvserver.raft.bean.LogEntry;
import com.tmh.kvserver.raft.bean.RaftStateEnum;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

/**
 * Raft实现
 */
@Slf4j
@Service
public class Raft implements InitializingBean {

    // Persistent state on all servers
    private int            currentTerm; // 当前term
    private int            votedFor; // 给谁投票
    private List<LogEntry> logEntrys = new ArrayList<>();

    // Volitale state on all servers
    private int commitIndex; // 提交位置
    private int lastApplied;// 最后应用的位置

    // Volitale state on leader
    private int[] nextIndex; // 将要发送给每个节点的下一个entry index
    private int[] matchIndex; // 每个节点的接受的最大的index

    // Non-Paper Fields
    private volatile RaftStateEnum   raftState      = RaftStateEnum.Follower; // 节点角色
    private          ExecutorService pool           = Executors.newFixedThreadPool(100);
    private          Peer[]          peers; // 其他节点
    private          Lock            appendLock     = new ReentrantLock(); // 追加日志时的锁
    private          Condition       appendCondtion = appendLock.newCondition(); // 追加日志时唤醒flw
    private          Thread          raftThread     = new Thread(new RaftMainLoop());// Raft的主线程


    @Autowired
    private StateMachine stateMachine; // 状态机

    @Value(value = "${server.peers}")
    private String serverPeers;

    @Override
    public void afterPropertiesSet() throws Exception {
        // TODO: 1.读配置文件 初始化peers 2.初始化各种Index
        initPeers();
        raftThread.start();
    }

    /**
     * 初始化其他节点信息
     */
    private void initPeers() {
        Properties properties = System.getProperties();
        Object port = properties.get("server.port");
        if (StringUtils.isEmpty(serverPeers)) {
            throw new IllegalArgumentException("server.peers must not null");
        }
        String[] configPeers = serverPeers.split(",");
        this.peers = new Peer[configPeers.length - 1];
        AtomicInteger index = new AtomicInteger(0);
        Arrays.asList(configPeers).forEach(server -> {
            if (!server.contains(port.toString())) {
                String[] hostConfigs = server.split(":");
                Peer peer = new Peer(hostConfigs[0], Integer.valueOf(hostConfigs[1]), Integer.valueOf(hostConfigs[2]));
                peers[index.get()] = peer;
                index.getAndIncrement();
            }
        });
        log.info("当前服务端口号:{},其他节点配置信息:{}", port, peers);
    }

    private class RaftMainLoop implements Runnable {

        @Override
        public void run() {
            for (; ; ) {
                if (raftState == RaftStateEnum.Follower) {
                    //Follower
                } else if (raftState == RaftStateEnum.Candidater) {
                    //Candidater
                } else {
                    //Leader
                }
            }
        }

    }

}