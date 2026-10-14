package com.example.hello_spring.common;

import lombok.Data;

@Data
public class Result<T> {
    private  Integer code;
    private  String msg;
    private  T data;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMsg("成功");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> success(){
        return success(null);
    }

    public static <T> Result<T> error(String msg){
        return error(400, msg);
    }

    /** 需要区分错误码时用它（如 413 文件过大、500 服务器内部错误） */
    public static <T> Result<T> error(int code, String msg){
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }

}
