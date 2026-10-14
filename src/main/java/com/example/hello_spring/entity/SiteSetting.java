package com.example.hello_spring.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 站点配置（键值对形式，方便以后加配置项不用改表结构） */
@Data
public class SiteSetting {
    private String settingKey;
    private String settingValue;
    private LocalDateTime updateTime;
}
