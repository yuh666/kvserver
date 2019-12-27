package com.tmh.kvserver.constants;

import com.tmh.kvserver.httpserver.controller.RaftClientController;
import com.tmh.kvserver.raft.bean.AppendEntriesRequest;
import com.tmh.kvserver.raft.bean.Command;
import com.tmh.kvserver.raft.bean.VoteRequest;

/**
 * @author <a href="mailto:zhouzhihui@ruubypay.com">wisdom</a>
 * Date: 2019-12-27 17:41
 * version: 1.0
 * Description:
 **/
public class RequestPathConstant {

    /**
     * 客户端 get请求路径 {@link com.tmh.kvserver.httpserver.controller.KvClientController#get(Command)}
     */
    public static final String CLI_GET_PATH = "/cli/get";

    /**
     * 客户端 put请求路径 {@link com.tmh.kvserver.httpserver.controller.KvClientController#put(Command)}
     */
    public static final String CLI_PUT_PATH = "/cli/put";

    /**
     * 客户端 remove请求路径 {@link com.tmh.kvserver.httpserver.controller.KvClientController#remove(Command)}
     */
    public static final String CLI_REMOVE_PATH = "/cli/remove";

    /**
     * 服务端 选举请求路径 {@link RaftClientController#requestForVote(VoteRequest)}
     */
    public static final String SERVER_REQUEST_VOTE_PATH = "/raft/request-vote";

    /**
     * 服务端 追加日志请求路径 {@link RaftClientController#appendEntries(AppendEntriesRequest)} }
     */
    public static final String SERVER_APPEND_ENTRIES_PATH = "/raft/append-entries";

}