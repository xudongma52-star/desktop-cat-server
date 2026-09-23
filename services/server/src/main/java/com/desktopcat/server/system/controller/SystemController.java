package com.desktopcat.server.system.controller;

import com.desktopcat.server.system.SystemStatus;
import java.time.Instant;
import org.springframework.boot.SpringBootVersion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/** 系统运行状态查询入口。 */
@RestController
//控制器接口的公共路径
@RequestMapping("/api/system")
public class SystemController {
    // 处理 GET /api/system/status，返回当前服务的运行信息。
    @GetMapping("/status")
    public SystemStatus status() {
        return new SystemStatus("UP", "desktop-cat-server", SpringBootVersion.getVersion(),
                System.getProperty("java.version"), Instant.now());
    }
}
