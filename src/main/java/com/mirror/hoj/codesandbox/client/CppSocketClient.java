package com.mirror.hoj.codesandbox.client;

import cn.hutool.json.JSONUtil;
import com.mirror.hoj.codesandbox.model.ExecuteCodeRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.*;
import java.net.Socket;

/**
 * CppSocket 客户端
 * 负责管理 Cpp代码沙箱的Socket 的连接、重连和断开。
 */
@Slf4j
@Component
public class CppSocketClient {

    @Value("${socket.remoteHost}")
    private String remoteHost;

    @Value("${socket.port}")
    private int port;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    /**
     * 获取可用的 Socket 连接。
     *
     * @return 已连接的 Socket 实例
     */
    public Socket getSocket() {
        ensureConnected(); // 每次调用时确保 Socket 已连接
        return socket;
    }

    private synchronized Boolean sendPing(){
        try {
            out.println(JSONUtil.toJsonStr("ping"));
            String response = in.readLine();
            if(!"pong".equals(response)){
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    /**
     * 确保 Socket 已连接。
     */
    private synchronized void ensureConnected() {
        try {
            // 服务端主动断开连接的话，这里不会感知到，还需要加一个心跳机制
            if (socket == null || socket.isClosed() || !socket.isConnected() || !sendPing()) {
                connectSocket();
            }
        } catch (Exception e) {
            log.error("Socket 连接失败", e);
            throw new RuntimeException("Socket 连接失败", e);
        }
    }

    /**
     * 建立新的 Socket 连接，并初始化流。
     */
    private void connectSocket() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        socket = new Socket(remoteHost, port);

        // 初始化输出和输入流
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        log.info("Socket 已连接到 {}:{}", remoteHost, port);
    }

    /**
     * 断开 Socket 连接。
     */
    @PreDestroy
    public synchronized void disconnect() {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
                log.info("Socket 已断开");
            } catch (IOException e) {
                log.warn("断开 Socket 时发生异常", e);
            }
        }
    }

    /**
     * 执行代码并返回结果。
     *
     * @param executeCodeRequest 执行代码的请求对象
     * @return 沙箱服务器返回的结果
     */
    public synchronized String executeCode(ExecuteCodeRequest executeCodeRequest) {
        ensureConnected();
        try {
            // 发送请求
            out.println(JSONUtil.toJsonStr(executeCodeRequest));

            // 读取响应
            String response = in.readLine();
            if (response == null) {
                log.warn("服务器连接已断开");
                throw new IOException("服务器连接已断开");
            }
            return response;
        } catch (IOException e) {
            log.error("Socket 通信异常", e);
            throw new RuntimeException("Socket 通信异常", e);
        }
    }

}
