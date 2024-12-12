package com.mirror.hoj.codesandbox.client;

import cn.hutool.json.JSONUtil;
import com.mirror.hoj.codesandbox.model.ExecuteCodeRequest;
import com.mirror.hoj.codesandbox.model.ExecuteCodeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * CppSocket 客户端 - 使用 Socket 池化管理
 */
@Slf4j
@Component
public class CppSocketClient {

    @Value("${socket.remoteHost}")
    private String remoteHost;

    @Value("${socket.port}")
    private int port;

    @Value("${socket.poolSize:10}")
    private int poolSize; // 默认池大小为10

    private BlockingQueue<Socket> socketPool;

    @PostConstruct
    public void init() {
        socketPool = new ArrayBlockingQueue<>(poolSize);
        try {
            for (int i = 0; i < poolSize; i++) {
                socketPool.offer(createSocket());
            }
            log.info("Socket 池初始化完成，大小：{}", poolSize);
        } catch (IOException e) {
            log.error("初始化 Socket 池失败", e);
            throw new RuntimeException("初始化 Socket 池失败", e);
        }
    }

    /**
     * 创建一个新的 Socket 连接。
     */
    private Socket createSocket() throws IOException {
        Socket socket = new Socket(remoteHost, port);
        log.info("创建新的 Socket 连接: {}:{}", remoteHost, port);
        return socket;
    }

    /**
     * 借用一个 Socket 连接。
     */
    private Socket borrowSocket() throws IOException {
        try {
            Socket socket = socketPool.poll();
            if (socket == null || socket.isClosed() || !socket.isConnected() || !sendPing(socket)) {
                log.info("Socket 不可用，创建新连接");
                return createSocket();
            }
            return socket;
        } catch (Exception e) {
            log.error("借用 Socket 失败", e);
            throw new IOException("借用 Socket 失败", e);
        }
    }

    /**
     * 归还一个 Socket 连接。
     */
    private void returnSocket(Socket socket) {
        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            log.warn("归还的 Socket 无效，直接丢弃");
            return;
        }
        if (!socketPool.offer(socket)) {
            log.warn("Socket 池已满，关闭多余的连接");
            try {
                socket.close();
            } catch (IOException e) {
                log.error("关闭多余 Socket 失败", e);
            }
        }
    }

    /**
     * 发送 Ping 检查 Socket 是否可用。
     */
    private boolean sendPing(Socket socket) {
        try {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println("ping");
            String response = in.readLine();
            return "pong".equals(response);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 执行代码并返回结果。
     *
     * @param executeCodeRequest 执行代码的请求对象
     * @return 沙箱服务器返回的结果
     */
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        Socket socket = null;
        try {
            socket = borrowSocket();

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // 发送请求
            out.println(JSONUtil.toJsonStr(executeCodeRequest));

            // 读取响应
            String response = in.readLine();
            if (response == null) {
                throw new IOException("服务器连接已断开");
            }
            return JSONUtil.toBean(response, ExecuteCodeResponse.class);
        } catch (IOException e) {
            log.error("执行代码时发生 Socket 通信异常", e);
            throw new RuntimeException("Socket 通信异常", e);
        } finally {
            if (socket != null) {
                returnSocket(socket);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("关闭 Socket 池...");
        while (!socketPool.isEmpty()) {
            Socket socket = socketPool.poll();
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    log.warn("关闭 Socket 失败", e);
                }
            }
        }
        log.info("Socket 池已关闭");
    }
}
