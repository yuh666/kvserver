package com.tmh.kvserver.httpserver;


import com.tmh.kvserver.enums.ErrorCodeEnum;

public class Result<T> {


    private int    code;
    private String msg;
    private T      body;

    private Result(int code, String msg, T body) {
        this.code = code;
        this.msg = msg;
        this.body = body;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getBody() {
        return body;
    }

    public void setBody(T body) {
        this.body = body;
    }

    public Result(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public static <T> Result<T> success(T body) {
        return new Result<T>(ErrorCodeEnum.SUCCESS.getCode(), ErrorCodeEnum.SUCCESS.getMessage(), body);
    }

    public static <T> Result<T> success() {
        return new Result<>(ErrorCodeEnum.SUCCESS.getCode(), ErrorCodeEnum.SUCCESS.getMessage());
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<T>(code, msg, null);
    }

}