package com.fufu.terminal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * SSH 远程主机监控服务，支持高/低频切换、缓存、降级处理。
 * <p>
 * 该服务通过 SSH 连接定期采集主机系统与 Docker 状态，并通过 WebSocket 推送到前端。
 * 支持高频（实时）和低频（降级）两种监控模式，具备缓存与自动降级能力。
 *
 * @author
 */
@Slf4j
@Service
public class SshMonitorService {

    private static final Duration HIGH_FREQ = Duration.ofSeconds(3);
    private static final Duration LOW_FREQ  = Duration.ofSeconds(30);
    private static final String DELIMITER   = "---CMD_DELIMITER---";

    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final SshCommandService sshCommandService;

    /**
     * 构造方法，注入依赖。
     *
     * @param objectMapper        Jackson 对象映射器
     * @param scheduler           监控任务调度线程池
     * @param sshCommandService   SSH 命令执行服务
     */
    public SshMonitorService(ObjectMapper objectMapper,
                             @Qualifier("monitorScheduler") ScheduledExecutorService scheduler,
                             SshCommandService sshCommandService) {
        this.objectMapper = objectMapper;
        this.scheduler = scheduler;
        this.sshCommandService = sshCommandService;
    }

    /**
     * 开始高频监控：立即发送缓存并以高频率推送后续数据。
     *
     * @param session WebSocket 会话
     * @param conn    SSH 连接包装
     */
    public void handleMonitorStart(WebSocketSession session, SshConnection conn) {
        log.info("Session {} 启动高频监控", session.getId());
        sendCached(session, conn);
        schedule(conn, session, HIGH_FREQ, true, false);
    }

    /**
     * 停止监控（降级为低频，仅更新缓存，不推送）。
     *
     * @param conn SSH 连接包装
     */
    public void handleMonitorStop(SshConnection conn) {
        if (conn != null) {
            log.info("降级到低频监控");
            schedule(conn, null, LOW_FREQ, false, false);
        }
    }

    /**
     * 使用重试模式启动监控：遇到 exec 通道冲突时降级处理。
     *
     * @param session WebSocket 会话
     * @param conn    SSH 连接包装
     */
    public void handleMonitorStartWithSeparateConnection(WebSocketSession session, SshConnection conn) {
        log.info("Session {} 启动带重试监控", session.getId());
        sendCached(session, conn);
        schedule(conn, session, HIGH_FREQ, true, true);
    }

    /**
     * 统一调度接口：取消旧任务，创建新任务。
     *
     * @param conn       SSH 连接包装
     * @param session    WebSocket 会话（可为 null）
     * @param period     调度周期
     * @param push       是否推送到客户端
     * @param withRetry  是否启用 fallback 重试
     */
    private void schedule(SshConnection conn, WebSocketSession session,
                          Duration period, boolean push, boolean withRetry) {
        conn.cancelMonitoringTask();
        Runnable task = () -> {
            try {
                Session js = conn.getJschSession();
                if (js == null || !js.isConnected()) {
                    log.warn("SSH 会话断开，停止监控");
                    conn.cancelMonitoringTask();
                    return;
                }

                Map<String, Object> data = withRetry
                        ? collectWithFallback(conn)
                        : collectSystemAndDocker(js);

                conn.setLastMonitorStats(data);
                if (push && session != null && session.isOpen()) {
                    sendMessage(session, "monitor_update", data);
                } else if (push) {
                    log.warn("Session 无效，自动降级低频");
                    schedule(conn, null, LOW_FREQ, false, false);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.info("监控任务中断，停止执行");
                conn.cancelMonitoringTask();
            } catch (Exception e) {
                log.error("监控执行异常，取消任务", e);
                conn.cancelMonitoringTask();
            }
        };

        Future<?> future = scheduler.scheduleAtFixedRate(task, 0,
                period.toSeconds(), TimeUnit.SECONDS);
        conn.setMonitoringTask(future);
        log.info("监控已调度：周期={}s，推送={}", period.toSeconds(), push);
    }

    /**
     * 先尝试正常收集，失败时返回降级数据。
     *
     * @param conn SSH 连接包装
     * @return 监控数据 Map
     */
    private Map<String, Object> collectWithFallback(SshConnection conn) {
        try {
            return collectSystemAndDocker(conn.getJschSession());
        } catch (Exception e) {
            log.warn("收集失败，返回降级数据：{}", e.getMessage());
            return Map.of(
                    "cpuModel", "监控不可用",
                    "uptime", "SSH 通道冲突",
                    "cpuUsage", 0.0,
                    "memoryUsage", Map.of("used",0L,"total",0L,"percentage",0.0),
                    "diskUsage",   Map.of("used",0L,"total",0L,"percentage",0.0),
                    "networkStats", Map.of("rx","N/A","tx","N/A"),
                    "dockerContainers", Collections.emptyList()
            );
        }
    }

    /**
     * 通过 SSH 执行命令并解析系统与 Docker 状态。
     *
     * @param js SSH Session
     * @return 监控数据 Map
     * @throws Exception 命令执行或解析异常
     */
    private Map<String, Object> collectSystemAndDocker(Session js) throws Exception {
        if (js == null || !js.isConnected()) {
            return Collections.emptyMap();
        }
        List<String> initCmds = List.of(
                "cat /proc/cpuinfo | grep 'model name' | uniq | sed 's/model name\\s*:\\s*//'",
                "uptime -p",
                "grep 'cpu ' /proc/stat",
                "free -m",
                "df -P /",
                "cat /proc/net/dev",
                "command -v docker >/dev/null && docker ps --format '{{.ID}}\\t{{.Names}}\\t{{.Status}}' || echo no_docker",
                "command -v docker >/dev/null && docker stats --no-stream --format '{{.ID}}\\t{{.CPUPerc}}\\t{{.MemUsage}}' || echo no_docker"
        );
        String init = String.join(" ; echo '" + DELIMITER + "'; ", initCmds);
        CommandResult r1 = sshCommandService.executeInternal(js, init);
        String[] p1 = r1.stdout().split(DELIMITER);

        Thread.sleep(1000);

        List<String> finalCmds = List.of("grep 'cpu ' /proc/stat", "cat /proc/net/dev");
        String fin = String.join(" ; echo '" + DELIMITER + "'; ", finalCmds);
        CommandResult r2 = sshCommandService.executeInternal(js, fin);
        String[] p2 = r2.stdout().split(DELIMITER);

        return parseStats(p1, p2);
    }

    /**
     * 将初始与后续命令输出解析为监控指标。
     *
     * @param init 初始命令输出
     * @param fin  后续命令输出
     * @return 监控数据 Map
     */
    private Map<String, Object> parseStats(String[] init, String[] fin) {
        Map<String, Object> m = new HashMap<>(8);
        m.put("cpuModel", getPart(init,0,"N/A"));
        m.put("uptime", getPart(init,1,"").replace("up ","").trim());
        m.put("cpuUsage", parseCpu(getPart(init,2,""), getPart(fin,0,"")));
        m.put("memoryUsage", parseMemDetailed(getPart(init,3,"")));
        m.put("diskUsage",   parseDiskDetailed(getPart(init,4,"")));
        m.put("networkStats", parseNet(getPart(init,5,""), getPart(fin,1,"")));
        m.put("dockerContainers", parseDocker(getPart(init,6,""), getPart(init,7,"")));
        return m;
    }

    /* ----------- 通用发送与工具方法 ----------- */

    /**
     * 发送缓存的监控数据。
     *
     * @param session WebSocket 会话
     * @param conn    SSH 连接包装
     */
    private void sendCached(WebSocketSession session, SshConnection conn) {
        Map<String,Object> cache = conn.getLastMonitorStats();
        if (cache != null && !cache.isEmpty()) {
            sendMessage(session, "monitor_update", cache);
        }
    }

    /**
     * 统一封装发送 JSON。
     *
     * @param session WebSocket 会话
     * @param type    消息类型
     * @param payload 消息内容
     */
    private void sendMessage(WebSocketSession session, String type, Object payload) {
        try {
            Map<String, Object> resp = Map.of("type", type, "payload", payload);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(resp)));
        } catch (IOException e) {
            log.error("发送 {} 失败", type, e);
        }
    }

    /**
     * 获取数组指定下标元素，若不存在则返回默认值。
     *
     * @param arr 数组
     * @param idx 下标
     * @param def 默认值
     * @return 元素或默认值
     */
    private String getPart(String[] arr, int idx, String def) {
        return (arr!=null && arr.length>idx) ? arr[idx].trim() : def;
    }

    /**
     * 解析 CPU 使用率。
     *
     * @param s1 初始 /proc/stat 行
     * @param s2 1 秒后 /proc/stat 行
     * @return CPU 使用率百分比
     */
    private double parseCpu(String s1, String s2) {
        if (s1.isEmpty()||s2.isEmpty()) return 0.0;
        try {
            long[] a=Arrays.stream(s1.split("\\s+")).skip(1).mapToLong(Long::parseLong).toArray();
            long[] b=Arrays.stream(s2.split("\\s+")).skip(1).mapToLong(Long::parseLong).toArray();
            long totA=Arrays.stream(a).sum(), totB=Arrays.stream(b).sum();
            long idleA=a[3], idleB=b[3];
            double diff=totB-totA, idle=idleB-idleA;
            return diff>0 ? (diff-idle)*100.0/diff : 0.0;
        } catch(Exception e){ log.warn("CPU 解析失败",e); return 0.0;}
    }

    /**
     * 解析内存使用详情。
     *
     * @param out free -m 输出
     * @return 内存使用 Map
     */
    private Map<String,Object> parseMemDetailed(String out) {
        Map<String,Object> r=new HashMap<>(3);
        if (out.isEmpty()) {
            r.put("used",0L);r.put("total",0L);r.put("percentage",0.0);
            return r;
        }
        try {
            // 解析 free -m 输出
            // 格式通常为:
            //               total        used        free      shared  buff/cache   available
            // Mem:           15867        1234       13456          45        1177       14145
            String[] lines = out.split("\\n");
            String memLine = null;
            for (String line : lines) {
                if (line.trim().startsWith("Mem:")) {
                    memLine = line.trim();
                    break;
                }
            }
            
            if (memLine != null) {
                String[] parts = memLine.split("\\s+");
                // parts[0] = "Mem:", parts[1] = total, parts[2] = used
                if (parts.length >= 3) {
                    long total = Long.parseLong(parts[1]);
                    long used = Long.parseLong(parts[2]);
                    double pct = total > 0 ? used * 100.0 / total : 0.0;
                    r.put("used", used);
                    r.put("total", total);
                    r.put("percentage", pct);
                    log.debug("内存解析成功: total={}MB, used={}MB, percentage={}%", total, used, pct);
                } else {
                    log.warn("内存行格式不正确: parts.length={}, line='{}'", parts.length, memLine);
                    r.put("used",0L);r.put("total",0L);r.put("percentage",0.0);
                }
            } else {
                log.warn("未找到内存信息行，free -m 输出: '{}'", out);
                r.put("used",0L);r.put("total",0L);r.put("percentage",0.0);
            }
        } catch(Exception e){ 
            log.warn("内存解析失败，free -m 输出: '{}', 错误: {}", out, e.getMessage(), e); 
            r.put("used",0L);r.put("total",0L);r.put("percentage",0.0);
        }
        return r;
    }

    /**
     * 解析磁盘使用详情。
     *
     * @param out df -P / 输出
     * @return 磁盘使用 Map
     */
    private Map<String,Object> parseDiskDetailed(String out) {
        Map<String,Object> r=new HashMap<>(3);
        if (out==null||out.isBlank()){
            r.put("used",0L);r.put("total",0L);r.put("percentage",0.0);
            return r;
        }
        try {
            String[] lines=out.trim().split("\\n");
            String[] p=lines[1].trim().split("\\s+");
            long total=Long.parseLong(p[1]), used=Long.parseLong(p[2]);
            double pct=Double.parseDouble(p[4].replace("%",""));
            r.put("used",used);r.put("total",total);r.put("percentage",pct);
        } catch(Exception e){ log.warn("Disk 解析失败",e); r.put("used",0L);r.put("total",0L);r.put("percentage",0.0);}
        return r;
    }

    /**
     * 解析网络流量：提取公用逻辑到 sumNetworkBytes。
     *
     * @param s1 初始 /proc/net/dev 输出
     * @param s2 1 秒后 /proc/net/dev 输出
     * @return 网络流量 Map
     */
    private Map<String,String> parseNet(String s1, String s2) {
        if (s1.isEmpty()||s2.isEmpty()) {
            return Map.of("rx","N/A","tx","N/A");
        }
        try {
            long[] stats1 = sumNetworkBytes(s1);
            long[] stats2 = sumNetworkBytes(s2);
            return Map.of(
                    "rx", format(stats2[0] - stats1[0]) + "/s",
                    "tx", format(stats2[1] - stats1[1]) + "/s"
            );
        } catch (Exception e) {
            log.warn("Net 解析失败", e);
            return Map.of("rx","N/A","tx","N/A");
        }
    }

    /**
     * 汇总 /proc/net/dev 中所有非 lo 接口的收发字节。
     *
     * @param netDevOutput /proc/net/dev 输出
     * @return [接收字节总数, 发送字节总数]
     */
    private long[] sumNetworkBytes(String netDevOutput) {
        return Arrays.stream(netDevOutput.split("\\n"))
                .filter(line -> line.contains(":") && !line.contains("lo:"))
                .map(line -> line.trim().split("\\s+"))
                .filter(parts -> parts.length > 9)
                .collect(
                        () -> new long[2],
                        (acc, parts) -> {
                            try {
                                acc[0] += Long.parseLong(parts[1]);
                                acc[1] += Long.parseLong(parts[9]);
                            } catch (NumberFormatException ignored) { }
                        },
                        (acc1, acc2) -> {
                            acc1[0] += acc2[0];
                            acc1[1] += acc2[1];
                        }
                );
    }

    /**
     * 字节数格式化为带单位字符串。
     *
     * @param b 字节数
     * @return 格式化字符串
     */
    private String format(long b){
        if (b<1024) return b+" B";
        int e=(int)(Math.log(b)/Math.log(1024));
        String pre="KMGTPE".charAt(e-1)+"i";
        return String.format("%.1f %sB", b/Math.pow(1024,e), pre);
    }

    /**
     * 使用 Stream API 更优雅地解析 Docker 容器信息。
     *
     * @param psOutput    docker ps 输出
     * @param statsOutput docker stats 输出
     * @return 容器信息列表
     */
    private List<Map<String,String>> parseDocker(String psOutput, String statsOutput) {
        if ("no_docker".equals(psOutput) || psOutput.trim().isEmpty()) {
            return Collections.emptyList();
        }
        // 步骤1：解析 docker ps
        Map<String, Map<String, String>> containers = Arrays.stream(psOutput.split("\\n"))
                .filter(line -> !line.isBlank())
                .map(line -> line.split("\t"))
                .filter(parts -> parts.length >= 3)
                .collect(Collectors.toMap(
                        parts -> parts[0],
                        parts -> new HashMap<>(Map.of(
                                "id", parts[0],
                                "name", parts[1],
                                "status", parts[2]
                        )),
                        (exist, repl) -> exist
                ));

        // 步骤2：解析 docker stats 并合并
        Arrays.stream(statsOutput.split("\\n"))
                .filter(line -> !line.isBlank())
                .map(line -> line.split("\t"))
                .filter(parts -> parts.length >= 3)
                .forEach(parts -> {
                    Map<String,String> info = containers.get(parts[0]);
                    if (info != null) {
                        info.put("cpuPerc", parts[1]);
                        info.put("memPerc", parts[2].split(" / ")[0]);
                    }
                });

        return new ArrayList<>(containers.values());
    }
}
