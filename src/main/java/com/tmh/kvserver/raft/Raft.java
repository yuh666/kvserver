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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    private          Lock            voteLock       = new ReentrantLock(); // 投票选举时的锁
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

    @Value("${server.port}")
    private String serverPort;

    @Value("${server.peers}")
    private String serverPeers;

    @Autowired
    private StateMachine stateMachine; // 状态机

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
        String[] configPeers = serverPeers.split(",");
        this.peers = new Peer[configPeers.length - 1];
        int index = 0;
        int port = Integer.parseInt(serverPort);
        for (String config : configPeers) {
            String[] hostConfigs = config.split(":");
            Peer peer = new Peer(hostConfigs[0], Integer.valueOf(hostConfigs[1]), Integer.valueOf(hostConfigs[2]));
            if (port != peer.getPort()) {
                peers[index++] = peer;
            } else {
                currentPeer = peer;
            }
        }
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
        try {
            log.info("vote request current node term:{} votedFor:[{}]", currentTerm, votedFor);
            log.info("vote request candidater node request param term:{} , candidateId:{} ", request.getTerm(), request.getCandidateId());
            VoteResponse voteResponse = new VoteResponse(currentTerm, Boolean.FALSE);
            // 该节点参与其他线程投票选举,返回
            if (!voteLock.tryLock()) {
                return voteResponse;
            }

            // 选举节点任期号没有自己新
            if (request.getTerm() < currentTerm) {
                return voteResponse;
            }

            // 当前节点没有投票或投票的是选举节点
            if ((votedFor == null || votedFor == request.getCandidateId())) {
                // 判断日志 是否一样新

                // 当前节点日志条目不为空
                if (!CollectionUtils.isEmpty(logEntrys)) {
                    LogEntry currentLastLogEntry = logEntrys.get(logEntrys.size() - 1);
                    // cd lastTerm大于日志条目最后entry的term
                    if (currentLastLogEntry.getTerm() > request.getLastTerm()) {
                        return voteResponse;
                    }

                    // cd term >= currentTerm ,cd lastIndex大于日志条目最后entry的index
                    if (currentLastLogEntry.getIndex() > request.getLastIndex()) {
                        return voteResponse;
                    }
                }
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
            return voteResponse;
        } finally {
            voteLock.unlock();
        }
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
                log.info("\n==============================================================  start ===============================================================================");
                log.info("current node term:{}  votedFor:{}  raftState:{} , peer:{} ", currentTerm, votedFor, raftState.getCode(), GsonUtils.toJson(currentPeer));
                if (raftState == RaftStateEnum.Follower) {
                    int heartbeatWaitTime = random.nextInt(heartBeatTick) + heartBeatTick;
                    log.info("current node heartbeatTime:{} ", heartbeatWaitTime + "ms");
                    try {
                        TimeUnit.MILLISECONDS.sleep(heartbeatWaitTime);
                        if (!flag) {
                            // 未接收append 等待超时
                            log.info("current node heartbeatTime timeout, become candidater ");
                            raftState = RaftStateEnum.Candidater;
                        }
                    } catch (InterruptedException e) {
                        log.info("current node receive leader append reset heartbeatTime");
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
                    for (Runnable requestForVoteThread : requestForVoteThreads) {
                        pool.submit(requestForVoteThread);
                    }
                    try {
                        // 选举超时时间
                        int electionTime = random.nextInt(Raft.this.electionTime) + Raft.this.electionTime;
                        log.info("current node start election ,term:{} election time: {} ", currentTerm, electionTime + "ms");
                        countDownLatch.await(electionTime, TimeUnit.MILLISECONDS);
                        if (countDownLatch.getCount() == 0) {
                            //  如果接收到大多数服务器的选票，那么就变成领导人
                            log.info("election successful become leader");
                            raftState = RaftStateEnum.Leader;
                        } else {
                            // 如果选举过程超时，再次发起一轮选举
                            log.info("election timeout , start next election");
                        }
                    } catch (InterruptedException e) {
                        // 如果接收到来自新的领导人的附加日志 RPC，转变成跟随者
                        log.info("find new leader become follower, leaderId:{}", votedFor);
                        raftState = RaftStateEnum.Follower;
                    }

                } else {
                    //Leader
                    log.info("current node become leader");
                    log.info("current node start append request");
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