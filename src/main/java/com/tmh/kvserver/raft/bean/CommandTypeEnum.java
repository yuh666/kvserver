package com.tmh.kvserver.raft.bean;

import com.tmh.kvserver.constants.RequestPathConstant;
import lombok.Getter;

@Getter
public enum CommandTypeEnum {

    Set(0, RequestPathConstant.CLI_PUT_PATH),
    Remove(1, RequestPathConstant.CLI_REMOVE_PATH),
    Noop(2)/*空操作*/, GET(3, RequestPathConstant.CLI_GET_PATH)/*封装get请求使用*/;

    private int code;

    private String path;

    CommandTypeEnum(int code) {
        this.code = code;
    }

    CommandTypeEnum(int code, String path) {
        this.code = code;
        this.path = path;
    }

    public static String getPathByCode(int code) {
        for (CommandTypeEnum typeEnum : CommandTypeEnum.values()) {
            if (typeEnum.code == code) {
                return typeEnum.getPath();
            }
        }
        return null;
    }

}