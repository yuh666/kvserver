package com.tmh.kvserver.raft.bean;

import lombok.Getter;

@Getter
public enum RaftStateEnum {

    Leader(0), Follower(1), Candidater(2);

    private int code;

    RaftStateEnum(int code) {
        this.code = code;
    }

    public static RaftStateEnum getByCode(int code) {
        for (RaftStateEnum typeEnum : RaftStateEnum.values()) {
            if (typeEnum.code == code) {
                return typeEnum;
            }
        }
        return null;
    }

}