package com.tmh.kvserver.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author <a href="mailto:zhouzhihui@ruubypay.com">wisdom</a>
 * Date: 2019-12-26 17:54
 * version: 1.0
 * Description:
 **/
@AllArgsConstructor
public enum ErrorCodeEnum {

    SUCCESS(200, "ok"),

    FAIl(500, "fail"),
    ;

    @Getter
    private int    code;
    @Getter
    private String message;

}