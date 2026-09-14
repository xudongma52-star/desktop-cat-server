package com.desktopcat.server.system;

import java.time.Instant;

//java记录类
//record专门用于承载数据的类型，减少手写样板代码
public record SystemStatus(String status, String application, String springBootVersion,
                           String javaVersion, Instant timestamp) {
}
