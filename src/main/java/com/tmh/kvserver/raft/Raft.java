package com.tmh.kvserver.raft;

import com.tmh.kvserver.constants.RequestPathConstant;
import com.tmh.kvserver.enums.ErrorCodeEnum;
import com.tmh.kvserver.httpserver.Result;
import com.tmh.kvserver.raft.bean.*;
import com.tmh.kvserver.utils.GsonUtils;
import com.tmh.kvserver.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.*;
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
    private List<LogEntry> logEntrys;
    private Peer           currentPeer; // 当前节点信息

    // Volitale state on all servers
    private int commitIndex; // 提交位置
    private int lastApplied;// 最后应用的位置

    // Volitale state on leader TODO 实际为为数组为了方便
    private Map<Peer, Integer> nextIndexs; // 将要发送给每个节点的下一个entry index
    private Map<Peer, Integer> matchIndexs; // 每个节点的接受的最大的index

    // Non-Paper Fields
    private volatile RaftStateEnum   raftState; // 节点角色
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
    private int heartBeatTick = 10 * 1000;

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
        initInnerState();
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

    private void initInnerState() {
        this.raftState = RaftStateEnum.Follower;
        this.currentTerm = 0;
        this.logEntrys = new ArrayList<>();
        logEntrys.add(new LogEntry(0, 0, null));// 虚拟节点
    }

    /**
     * 接收选举请求 1.如果term < currentTerm返回 false 2.如果 votedFor 为空或者为
     * candidateId，并且候选人的日志至少和自己一样新，那么就投票给他
     *
     * @param request 选举请求参数
     */
    public VoteResponse requestForVote(VoteRequest request) {
        try {
            log.info("vote request node term:{} votedFor:[{}]", currentTerm, votedFor);
            log.info("vote request candidater node request param term:{} , candidateId:{} ", request.getTerm(),
                     request.getCandidateId());
            VoteResponse voteResponse = new VoteResponse(currentTerm, Boolean.FALSE);
            // 该节点参与其他线程投票选举,等待
            voteLock.lock();
            // 选举节点任期号没有自己新
            if (request.getTerm() < currentTerm) {
                return voteResponse;
            }

            // 判断日志是否是更新
            boolean upToDate = false;
            if (request.getLastTerm() > getLastTerm()
                    || (request.getLastTerm() == getLastTerm() && request.getLastIndex() >= getLastIndex())) {
                upToDate = true;
            }

            // 为空 or 就是本节点 && 日志更新
            if ((votedFor == null || votedFor == request.getCandidateId()) && upToDate) {
                /**
                 * 投票 修改节点状态为flw 修改votedFor为cd 如果当前节点参与选举则唤醒
                 */
                this.currentTerm = request.getTerm();
                votedFor = request.getCandidateId();
                voteResponse.setVoteGranted(true);
                voteResponse.setTerm(this.currentTerm);
                raftState = RaftStateEnum.Follower;
                raftThread.interrupt();
            }
            log.info("vote result :{}", GsonUtils.toJson(voteResponse));
            return voteResponse;
        } finally {
            voteLock.unlock();
        }
    }

    /**
     * 接收append请求
     * <p>
     * 如果 term < currentTerm 就返回 false （5.1 节） 如果日志在 prevLogIndex 位置处的日志条目的任期号和
     * prevLogTerm 不匹配，则返回 false （5.3 节）
     * 如果已经存在的日志条目和新的产生冲突（索引值相同但是任期号不同），删除这一条和之后所有的 （5.3 节） 附加日志中尚未存在的任何新条目 如果
     * leaderCommit > commitIndex，令 commitIndex 等于 leaderCommit 和 新日志条目索引值中较小的一个
     *
     * @param request
     */
    public AppendEntriesResponse appendEntries(AppendEntriesRequest request) {
        try {
            AppendEntriesResponse response = new AppendEntriesResponse(currentTerm, Boolean.FALSE);
            if (!appendLock.tryLock()) {
                return response;
            }
            // 1.term < currentTerm
            if (request.getTerm() < currentTerm) {
                return response;
            }

            log.info("node receive leader append request node term:{} , leader term :{} ,node become follower",
                     currentTerm, request.getTerm());
            raftState = RaftStateEnum.Follower;
            currentTerm = request.getTerm();
            votedFor = request.getLeaderId();

            // 2.该节点刚成为ld 发送空append心跳
            List<LogEntry> leaderEntries = request.getEntries();
            if (CollectionUtils.isEmpty(leaderEntries)) {
                log.info("node receive leader empty rpc request");
                response.setSuccess(Boolean.TRUE);
                return response;
            }

            log.info("node receive not empty rpc request param:{} , current node logEntry:{} ", GsonUtils.toJson(request), GsonUtils.toJson(logEntrys));
            // 3.判断prevLogIndex是否在logEntrys中存在
            // 如果日志在 prevLogIndex 位置处的日志条目的任期号和 prevLogTerm 不匹配，则返回 false
            int prevLogIndex = request.getPrevLogIndex();
            if (prevLogIndex != 0) {
                LogEntry prevLogEntry;
                if ((prevLogEntry = this.getLog(prevLogIndex)) != null) {
                    // 存在 比较任期号
                    if (prevLogEntry.getTerm() != request.getPrevLogTerm()) {
                        // index相同任期号不同
                        log.info("node exist prevLogIndex log but term difference ");
                        return response;
                    }
                } else {
                    log.info("node not exist prevLogIndex log need reduce nextIndex ");
                    // 减少nextIndex
                    return response;
                }
            }

            log.info("node exist prevLogIndex log ");
            // 4.解决日志冲突prevLogIndex到logEntrys中prevLogIndex之后的数据删除
            // 如果已经存在的日志条目和新的产生冲突（索引值相同但是任期号不同），删除这一条和之后所有的
            if (prevLogIndex < logEntrys.size() - 1) {
                // 当前 flw prevLogIndex之后存在log 比较prevLogIndex+1的term和append entries[0] term
                if (this.getLogTerm(prevLogIndex + 1) != leaderEntries.get(0).getTerm()) {
                    log.info("follower exist prevLogIndex after log ,but next index term difference need delete [{} => {}] log ", prevLogIndex + 1, logEntrys.size() - 1);
                    // 索引值相同,term不同
                    // 跟ld匹配的日志
                    List<LogEntry> newLogEntrys = logEntrys.subList(0, prevLogIndex);

                    // 删除prevLogIndex这一条和之后的数据,并应用到状态机
                    for (int i = prevLogIndex + 1; i < logEntrys.size(); i++) {
                        LogEntry logEntry = logEntrys.get(i);
                        LogEntry delLogEntry = new LogEntry();
                        BeanUtils.copyProperties(logEntry, delLogEntry);
                        delLogEntry.getCommand().setCommandType(CommandTypeEnum.Remove.getCode());
                        stateMachine.apply(delLogEntry);
                    }
                    // 更新状态机日志
                    this.logEntrys = newLogEntrys;
                } else {
                    log.info("follower exist prevLogIndex after log but same not append log");
                    // flw存在当前日志不需要追加写 附加日志中尚未存在的任何新条目
                    response.setSuccess(Boolean.TRUE);
                    return response;
                }
            }

            log.info("follower write leader all logEntrys and flush stateMachine ");
            // 写leader 日志到日志文件和状态机中
            logEntrys.addAll(leaderEntries);
            for (LogEntry logEntry : leaderEntries) {
                stateMachine.apply(logEntry);
            }

            // 如果 leaderCommit > commitIndex，令 commitIndex 等于 leaderCommit 和 新日志条目索引值中较小的一个
            int leaderCommit = request.getLeaderCommit();
            if (leaderCommit > commitIndex) {
                log.info("leaderCommit more follower commitIndex , follower need update commitIndex");
                this.commitIndex = Math.min(leaderCommit, this.getLastIndex());
            }
            response.setSuccess(Boolean.TRUE);
            raftState = RaftStateEnum.Follower;

            return response;
        } finally {
            // 中断主线程心跳睡眠,重置心跳
            flag = true;
            raftThread.interrupt();
            appendLock.unlock();
        }

    }

    /**
     * 处理客户端请求
     *
     * @param command 操作
     * @return 结果
     */
    public synchronized Result handlerClientRequest(Command command) {
        Result<String> response = Result.success();
        // 是否重定向
        if (!this.isLeader()) {
            // 重定向 请求leader
            Peer leaderPeer = this.getLeaderPeer();
            if (leaderPeer == null) {
                // 此集群无leader 对外不提供服务
                log.warn("this cluster not leader ");
                response.setCode(ErrorCodeEnum.SERVICE_UNAVAILABLE.getCode());
                response.setMsg(ErrorCodeEnum.SERVICE_UNAVAILABLE.getMessage());
                return response;
            }
            log.info("node is not leader,redirect to {}", leaderPeer.getBasePath());
            return redirect(command);
        }

        // 处理客户端请求
        if (command.getCommandType() == CommandTypeEnum.GET.getCode()) {
            String key = command.getKey();
            log.info("node is leader , handler get request key:{} ", key);
            response.setBody(stateMachine.get(key));
            return response;
        }

        // 写本地日志 预提交(等待其他节点复制结果)
        this.write(command);
        log.info("logEntry {} write leader local logEntrys success", GsonUtils.toJson(this.getLastLog()));

        // 复制到其他节点,大多数节点复制成功 写入到状态机中
        LogEntry logEntry = new LogEntry(this.getLastIndex(), this.currentTerm, command);

        List<Future<Boolean>> futureList = new ArrayList<>();
        log.info("start send replication log append request");
        for (Peer peer : peers) {
            futureList.add(this.replication(peer, logEntry));
        }

        List<Boolean> resultList = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(peers.length);
        // 获取future中执行结果
        for (Future<Boolean> future : futureList) {
            pool.submit(() -> {
                try {
                    resultList.add(future.get(3000, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    resultList.add(false);
                } catch (ExecutionException e) {
                    e.printStackTrace();
                    resultList.add(false);
                } catch (TimeoutException e) {
                    e.printStackTrace();
                    resultList.add(false);
                } finally {
                    countDownLatch.countDown();
                }
            });
        }

        try {
            countDownLatch.await(4000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        log.info("leader send append log result:{}", resultList);
        // 判断是否是大多数节点复制完成
        int successCount = 0;
        for (Boolean status : resultList) {
            if (status) {
                successCount++;
            }
        }

        log.info("leader send append log successCount:{} ", successCount);
        /**
         * 如果存在一个满足N > commitIndex的 N，并且大多数的matchIndex[i] ≥ N成立，
         * 并且log[N].term == currentTerm成立，那么令 commitIndex 等于这个 N （5.3 和 5.4 节）
         */
        List<Integer> matchIndexList = new ArrayList<>(matchIndexs.values());
        Collections.sort(matchIndexList);
        Integer N = matchIndexList.get(matchIndexList.size() / 2);
        if (N > commitIndex && logEntrys.get(N).getTerm() == currentTerm) {
            log.info("exist N:{} update commitIndex ", N);
            commitIndex = N;
        }

        // 大多数节点复制成功则响应客户端
        if (successCount >= this.peers.length / 2) {
            log.info("leader append log receive majority follower response ,apply log to stateMachine");
            // 将logEntry应用到状态机
            // 更新commitIndex和lastApplied
            stateMachine.apply(logEntry);
            commitIndex = logEntry.getIndex();
            lastApplied = commitIndex;
        } else {
            // 复制失败 移除logEntry
            log.info("leader append log fail delete this logEntry");
            logEntrys.remove(logEntry.getIndex());
            response.setCode(ErrorCodeEnum.FAIl.getCode());
            response.setMsg(ErrorCodeEnum.FAIl.getMessage());
        }

        log.info("leader other node info nextIndexs:{}  matchIndexs:{} leaderCommitIndex:{}", nextIndexs.values(), matchIndexList, commitIndex);
        return response;
    }

    /**
     * 复制日志
     *
     * @param peer
     * @param newLogEntry
     * @return
     */
    private Future<Boolean> replication(Peer peer, LogEntry newLogEntry) {
        return pool.submit(() -> {
            long start = System.currentTimeMillis();
            long end = start;
            // 20秒重试
            int sendCount = 0;
            while (end - start < 20 * 1000) {
                String url = peer.getBasePath() + RequestPathConstant.SERVER_APPEND_ENTRIES_PATH;
                AppendEntriesRequest request = new AppendEntriesRequest();
                request.setLeaderId(currentPeer.getPeerId());
                request.setTerm(currentTerm);
                request.setLeaderCommit(commitIndex);

                // 日志信息
                request.setEntries(Collections.singletonList(newLogEntry));
                Integer nextIndex = nextIndexs.get(peer);
                List<LogEntry> requestLogEntries = new ArrayList<>();
                if (newLogEntry.getIndex() > nextIndex) {
                    // 发送日志[nextIndex->newLogEntry.index]
                    for (int i = nextIndex; i <= newLogEntry.getIndex(); i++) {
                        requestLogEntries.add(logEntrys.get(i));
                    }
                } else {
                    requestLogEntries.add(newLogEntry);
                }
                // logEntries中最小日志的上一个
                int minLogIndex = requestLogEntries.get(0).getIndex();
                request.setPrevLogIndex(this.logEntrys.get(minLogIndex - 1).getIndex());
                request.setPrevLogTerm(this.logEntrys.get(minLogIndex - 1).getTerm());
                request.setEntries(requestLogEntries);

                log.info("request append log {} => {} request:{} ", currentPeer.getPeerId(), peer.getPeerId(), GsonUtils.toJson(request));
                String resultStr = HttpClientUtil.restPost(url, GsonUtils.toJson(request));
                log.info("request append log reply {} => {} resultStr:{} ", peer.getPeerId(), currentPeer.getPeerId(), GsonUtils.toJson(resultStr));
                if (resultStr != null) {
                    Result result = GsonUtils.fromJson(resultStr, Result.class);
                    if (result.getCode() == ErrorCodeEnum.SUCCESS.getCode()) {
                        AppendEntriesResponse appendResponse = GsonUtils.fromJson(result.getBody().toString(), AppendEntriesResponse.class);
                        if (appendResponse.isSuccess()) {
                            log.info(" {} node replication success update nextIndexs and matchIndexs ", peer.getBasePath());
                            // 更新nextIndexs和matchIndexs
                            nextIndexs.put(peer, newLogEntry.getIndex() + 1);
                            matchIndexs.put(peer, newLogEntry.getIndex());
                            return true;
                        } else {
                            // append fail
                            int flwTerm = appendResponse.getTerm();
                            // flwTerm 大于自己 自己变为flw
                            if (flwTerm > currentTerm) {
                                log.info("follower term {} more leader term {} ,leader will become follower ", flwTerm, currentTerm);
                                raftState = RaftStateEnum.Follower;
                                currentTerm = flwTerm;
                                return false;
                            }

                            log.info("follower not exist nextIndex log , retry nextIndex:{} ", nextIndex - 1);
                            // 减小nextIndex
                            nextIndexs.put(peer, nextIndex - 1);
                        }
                    }
                } else {
                    // TODO 当follower 宕机不重试 可选择
                    log.warn("if result is null ,leader connection follower fail  ,so no retry");
                    return false;
                }
                ++sendCount;
                end = System.currentTimeMillis();
                log.info("request append log {} => {} retry time:{} , count:{} ", currentPeer.getPeerId(), peer.getPeerId(), (end - start), sendCount);
            }
            // 超时
            log.info("request append log {} => {} timeout", currentPeer.getPeerId(), peer.getPeerId());
            return false;
        });
    }

    /**
     * 重定向请求leader
     *
     * @param command 操作
     * @return 结果
     */
    private Result redirect(Command command) {
        String resultStr = HttpClientUtil.restPost(this.getLeaderPeer().getBasePath() + CommandTypeEnum.getPathByCode(command.getCommandType()), GsonUtils.toJson(command));
        if (resultStr == null) {
            return Result.fail(ErrorCodeEnum.FAIl.getCode(), ErrorCodeEnum.FAIl.getMessage());
        }
        return GsonUtils.fromJson(resultStr, Result.class);
    }


    private class RaftMainLoop implements Runnable {

        @Override
        public void run() {
            for (; ; ) {
                log.info(
                        "\n==============================================================  loop start ===============================================================================");
                log.info("node term:{}  votedFor:{}  raftState:{} , currentPeer:{} logEntrys:{}", currentTerm, votedFor,
                         raftState.name(), GsonUtils.toJson(currentPeer), GsonUtils.toJson(logEntrys));
                if (raftState == RaftStateEnum.Follower) {
                    doAsFollower();
                } else if (raftState == RaftStateEnum.Candidater) {
                    doAsCanditate();
                } else {
                    doAsLeader();
                }
                log.info(
                        "\n==============================================================  loop end ===============================================================================");
                log.info("node term:{}  votedFor:{}  raftState:{} , peer:{} ", currentTerm, votedFor,
                         raftState.name(), GsonUtils.toJson(currentPeer));
            }
        }

    }

    /**
     * Follower 行为
     */
    private void doAsFollower() {
        int heartbeatWaitTime = random.nextInt(heartBeatTick) + heartBeatTick;
        log.info("node heartbeatTime:{} ", heartbeatWaitTime + "ms");
        try {
            TimeUnit.MILLISECONDS.sleep(heartbeatWaitTime);
            if (!flag) {
                // 未接收append 等待超时
                log.info("node heartbeatTime timeout, become candidater ");
                raftState = RaftStateEnum.Candidater;
            }
        } catch (InterruptedException e) {
            log.info("node receive leader  request reset heartbeatTime");
            // 重置标志位 开始下一轮心跳
            flag = false;
        }
    }

    /**
     * 在转变成候选人后就立即开始选举过程 自增当前的任期号（currentTerm） 给自己投票 重置选举超时计时器 发送请求投票的 RPC 给其他所有服务器
     */
    private void doAsCanditate() {
        ++currentTerm;
        votedFor = currentPeer.getPeerId();
        List<Runnable> requestForVoteThreads = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch((peers.length + 1) >> 1);
        for (Peer peer : peers) {
            Runnable runnable = () -> {
                // 请其他节点投票
                VoteRequest voteRequest = new VoteRequest();
                voteRequest.setTerm(currentTerm);
                voteRequest.setCandidateId(currentPeer.getPeerId());
                voteRequest.setLastIndex(getLastIndex());
                voteRequest.setLastTerm(getLastTerm());
                VoteResponse response = peer.requestVote(voteRequest);
                if (response != null && response.isVoteGranted()) {
                    countDownLatch.countDown();
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
            log.info("node start election peerId:{},term:{} election time: {} ", currentPeer.getPeerId(), currentTerm,
                     electionTime + "ms");
            countDownLatch.await(electionTime, TimeUnit.MILLISECONDS);
            if (countDownLatch.getCount() == 0) {
                // 如果接收到大多数服务器的选票，那么就变成领导人
                log.info("election successful become leader");
                raftState = RaftStateEnum.Leader;
                // 充值matchIndex 和 nextIndexs
                resetLeaderIndex();
            } else {
                // 如果选举过程超时，再次发起一轮选举
                log.info("election timeout , start next election");
            }
        } catch (InterruptedException e) {
            // 如果接收到来自新的领导人的附加日志 RPC，转变成跟随者
            log.info("find new leader become follower, leaderId:{}", votedFor);
            raftState = RaftStateEnum.Follower;
        }
    }

    // TODO: 发送append请求
    // Leader
    private void doAsLeader() {
        log.info("node  leader");
        List<Runnable> heartbeatThreads = new ArrayList<>();
        for (Peer peer : peers) {
            Runnable runnable = () -> {
                String url = peer.getBasePath() + "/raft/append-entries";
                AppendEntriesRequest appendRequest = new AppendEntriesRequest();
                appendRequest.setTerm(currentTerm);
                appendRequest.setLeaderId(currentPeer.getPeerId());
                // 心跳 日志空
                appendRequest.setEntries(null);

                String requestParam = GsonUtils.toJson(appendRequest);
                String resultStr = HttpClientUtil.restPost(url, requestParam);
                if (!Objects.isNull(resultStr)) {
                    Result result = GsonUtils.fromJson(resultStr, Result.class);
                    if (result.getCode() == ErrorCodeEnum.SUCCESS.getCode()) {
                        // 请求成功
                        AppendEntriesResponse appendEntriesResponse = GsonUtils.fromJson(result.getBody().toString(),
                                                                                         AppendEntriesResponse.class);
                        if (appendEntriesResponse.isSuccess()) {
                            // 心跳成功 判断任期号,当前的任期号，用于领导人去更新自己
                            if (appendEntriesResponse.getTerm() > currentTerm) {
                                log.info("node will become follower,my term:{} response term:{}", currentTerm,
                                         appendEntriesResponse.getTerm());
                                raftState = RaftStateEnum.Follower;
                                votedFor = null;
                            }
                        }
                    }
                }
            };
            heartbeatThreads.add(runnable);
        }

        // 开始发送心跳
        log.info("node start append request");
        for (Runnable requestForVoteThread : heartbeatThreads) {
            pool.submit(requestForVoteThread);
        }

        // TODO 时间待修改
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void resetLeaderIndex() {
        this.matchIndexs = new HashMap<>(this.peers.length);
        this.nextIndexs = new HashMap<>(this.peers.length);
        for (Peer peer : this.peers) {
            nextIndexs.put(peer, getLastIndex() + 1);
            matchIndexs.put(peer, getLastIndex());
        }
    }

    public boolean isLeader() {
        return this.raftState.getCode() == RaftStateEnum.Leader.getCode();
    }

    private int getLastIndex() {
        return this.logEntrys.get(this.logEntrys.size() - 1).getIndex();
    }

    private int getLastTerm() {
        return this.logEntrys.get(this.logEntrys.size() - 1).getTerm();
    }

    private LogEntry getLastLog() {
        return this.logEntrys.get(this.logEntrys.size() - 1);
    }

    private LogEntry getLog(int index) {
        return index > logEntrys.size() - 1 ? null : logEntrys.get(index);
    }

    private int getLogTerm(int index) {
        return logEntrys.get(index).getTerm();
    }

    private int getLogIndex(int index) {
        return logEntrys.get(index).getIndex();
    }

    private Peer getLeaderPeer() {
        for (Peer peer : peers) {
            if (peer.getPeerId() == votedFor) {
                return peer;
            }
        }
        return null;
    }

    private void write(Command command) {
        LogEntry logEntry = new LogEntry();
        logEntry.setCommand(command);
        logEntry.setIndex(getLastIndex() + 1);
        logEntry.setTerm(currentTerm);
        logEntrys.add(logEntry);
    }
}