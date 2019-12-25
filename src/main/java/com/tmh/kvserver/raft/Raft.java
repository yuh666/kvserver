package com.tmh.kvserver.raft;

import com.tmh.kvserver.httpserver.Result;
import com.tmh.kvserver.raft.bean.*;
import com.tmh.kvserver.utils.GsonUtils;
import com.tmh.kvserver.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Raft实现
 */
@Slf4j
@Service
public class Raft implements InitializingBean {

    // Persistent state on all servers
    private int            currentTerm; // 当前term
    private Integer        votedFor; // 给谁投票
    private List<LogEntry> logEntrys = new ArrayList<>();
    private Peer           currentPeer; // 当前节点信息

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

    /**
     * 随机函数
     */
    private Random random = new Random();

    /**
     * 是否append标志位
     */
    private volatile boolean flag = Boolean.FALSE;

    /**
     * 心跳间隔时间 5s
     */
    private int heartBeatTick = 5 * 1000;

    /**
     * 选举间隔时间 15-30s
     */
    private int electionTime = 15 * 1000;

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
        if (port == null) {
            throw new IllegalArgumentException("server.port must not null, -Dserver.port");
        }
        if (StringUtils.isEmpty(serverPeers)) {
            throw new IllegalArgumentException("server.peers must not null");
        }
        String[] configPeers = serverPeers.split(",");
        this.peers = new Peer[configPeers.length - 1];
        AtomicInteger index = new AtomicInteger(0);
        Arrays.asList(configPeers).forEach(server -> {
            String[] hostConfigs = server.split(":");
            Peer peer = new Peer(hostConfigs[0], Integer.valueOf(hostConfigs[1]), Integer.valueOf(hostConfigs[2]));
            if (!server.contains(port.toString())) {
                peers[index.get()] = peer;
                index.getAndIncrement();
            } else {
                currentPeer = peer;
            }
        });
        log.info("当前服务端口号:{},其他节点配置信息:{}", port, peers);
    }

    /**
     * 接收选举请求
     * 1.如果term < currentTerm返回 false
     * 2.如果 votedFor 为空或者为 candidateId，并且候选人的日志至少和自己一样新，那么就投票给他
     *
     * @param request 选举请求参数
     */
    public VoteResponse requestForVote(VoteRequest request) {
        VoteResponse voteResponse = new VoteResponse();
        voteResponse.setVoteGranted(Boolean.FALSE);
        voteResponse.setTerm(currentTerm);
        if (request.getTerm() > currentTerm) {
            if ((votedFor == null || votedFor == request.getCandidateId())) {
                // 判断日志 是否一样新 TODO 待修改
                voteResponse.setVoteGranted(Boolean.TRUE);
                /**
                 * 修改节点状态为flw
                 * 修改votedFor为cd
                 * 如果当前节点参与选举则唤醒
                 */
                votedFor = request.getCandidateId();
                raftState = RaftStateEnum.Follower;
                raftThread.interrupt();
            }
        }
        return voteResponse;
    }

    /**
     * 接收append请求
     * <p>
     * 如果 term < currentTerm 就返回 false （5.1 节）
     * 如果日志在 prevLogIndex 位置处的日志条目的任期号和 prevLogTerm 不匹配，则返回 false （5.3 节）
     * 如果已经存在的日志条目和新的产生冲突（索引值相同但是任期号不同），删除这一条和之后所有的 （5.3 节）
     * 附加日志中尚未存在的任何新条目
     * 如果 leaderCommit > commitIndex，令 commitIndex 等于 leaderCommit 和 新日志条目索引值中较小的一个
     *
     * @param request
     */
    public void appendEntries(AppendEntriesRequest request) {
        // 重置主线程中的标志位
        flag = true;
        raftThread.interrupt();
    }

    private class RaftMainLoop implements Runnable {

        @Override
        public void run() {
            for (; ; ) {
                log.info("\n========================================");
                log.info("当前节点term:{},leaderId:{},raftState:{}", currentTerm, votedFor, raftState.getCode());

                if (raftState == RaftStateEnum.Follower) {
                    int heartbeatWaitTime = random.nextInt(heartBeatTick) + heartBeatTick;
                    log.info("flw heartbeatWaitTime:{}", heartbeatWaitTime + "ms");
                    try {
                        TimeUnit.MILLISECONDS.sleep(heartbeatWaitTime);
                        if (!flag) {
                            // 未接收append 等待超时
                            raftState = RaftStateEnum.Candidater;
                        }
                    } catch (InterruptedException e) {
                        continue;
                    }


                } else if (raftState == RaftStateEnum.Candidater) {
                    // Candidater
                    /**
                     * 在转变成候选人后就立即开始选举过程
                     * 自增当前的任期号（currentTerm）
                     * 给自己投票
                     * 重置选举超时计时器
                     * 发送请求投票的 RPC 给其他所有服务器
                     */
                    ++currentTerm;
                    votedFor = currentPeer.getPeerId();

                    List<Runnable> requestForVoteThreads = new ArrayList<>();
                    CountDownLatch countDownLatch = new CountDownLatch((peers.length + 1) >> 1);
                    for (Peer peer : peers) {
                        Runnable runnable = () -> {
                            String host = peer.getHost() + ":" + peer.getPort();
                            String url = "http://" + host + "/raft/request-vote";
                            VoteRequest voteRequest = new VoteRequest();
                            voteRequest.setTerm(currentTerm);
                            voteRequest.setCandidateId(peer.getPeerId());
                            // 日志条目
                            if (CollectionUtils.isEmpty(logEntrys)) {
                                voteRequest.setLastTerm(currentTerm);
                                voteRequest.setLastIndex(0);
                            } else {
                                LogEntry lastLogEntry = logEntrys.get(logEntrys.size() - 1);
                                voteRequest.setLastTerm(lastLogEntry.getTerm());
                                voteRequest.setLastIndex(lastLogEntry.getIndex());
                            }

                            String requestParam = GsonUtils.toJson(voteRequest);
                            String resultStr = HttpClientUtil.restPost(url, requestParam);
                            if (!Objects.isNull(resultStr)) {
                                log.info("cd选举请求节点:{},请求参数:{},响应信息:{}", host, requestParam, resultStr);
                                Result result = GsonUtils.fromJson(resultStr, Result.class);
                                if (result.getCode() == 0) {
                                    // 请求成功
                                    VoteResponse voteResponse = GsonUtils.fromJson(result.getBody().toString(), VoteResponse.class);
                                    if (voteResponse.isVoteGranted()) {
                                        // 投票成功
                                        countDownLatch.countDown();
                                    }
                                }
                            }
                        };
                        requestForVoteThreads.add(runnable);
                    }

                    // 开始投票请求
                    log.info("cd开始选举");
                    for (Runnable requestForVoteThread : requestForVoteThreads) {
                        pool.submit(requestForVoteThread);
                    }
                    try {
                        // 选举超时时间
                        int electionTime = random.nextInt(Raft.this.electionTime) + Raft.this.electionTime;
                        log.info("cd electionTime:{}", electionTime + "ms");
                        countDownLatch.await(electionTime, TimeUnit.MILLISECONDS);
                        if (countDownLatch.getCount() == 0) {
                            //  如果接收到大多数服务器的选票，那么就变成领导人
                            log.info("选举成功");
                            raftState = RaftStateEnum.Leader;
                        } else {
                            // 如果选举过程超时，再次发起一轮选举
                            log.info("选举超时,重新选举");
                        }
                    } catch (InterruptedException e) {
                        // 如果接收到来自新的领导人的附加日志 RPC，转变成跟随者
                        log.info("找到新的leader");
                        raftState = RaftStateEnum.Follower;
                    }

                } else {
                    //Leader
                    log.info("成为leader");
                    // TODO 发送append请求
                    try {
                        TimeUnit.MINUTES.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

}