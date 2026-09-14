package com.desktopcat.server.system;

import java.time.Instant;
import org.springframework.boot.SpringBootVersion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


//接受Http请求，并将方法返回的数据写入响应
@RestController
//控制器接口的公共路径
@RequestMapping("/api/system")
//处理get请求
public class SystemController {
    @GetMapping("/status")
    public SystemStatus status() {
        return new SystemStatus("UP", "desktop-cat-server", SpringBootVersion.getVersion(),
                System.getProperty("java.version"), Instant.now());
    }
}
