package com.tmh.kvserver.raft.bean;

import lombok.Getter;

@Getter
public enum CommandTypeEnum {

    Set(0), Remove(1), Noop(2)/*空操作*/;

    private int code;

    CommandTypeEnum(int code) {
        this.code = code;
    }

    public static CommandTypeEnum getByCode(int code) {
        for (CommandTypeEnum typeEnum : CommandTypeEnum.values()) {
            if (typeEnum.code == code) {
                return typeEnum;
            }
        }
        return null;
    }

}