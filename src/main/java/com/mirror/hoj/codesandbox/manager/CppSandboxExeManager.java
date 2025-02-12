package com.mirror.hoj.codesandbox.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class CppSandboxExeManager {

    private Process exeProcess;

    private final List<Runnable> startListeners = new CopyOnWriteArrayList<>();

    public void startExe() {
        try {
            String exePath = "/home/ubuntu/hoj-code-sandbox/cpp_sandbox/cpp_sandbox";
            ProcessBuilder processBuilder = new ProcessBuilder(exePath);
            processBuilder.inheritIO();  // 将子进程的输出流重定向到父进程的控制台
            exeProcess = processBuilder.start();
            log.info("C++沙箱程序已启动...");
            // 移动到client去处理
//            monitorProcess();  // 启动进程监控
            // 通知所有监听器
            for (Runnable listener : startListeners) {
                listener.run();
            }
        } catch (IOException e) {
            log.error("C++沙箱程序启动失败!",e);
        }
    }

    public void stopExe() {
        if (exeProcess != null && exeProcess.isAlive()) {
            exeProcess.destroy();
            log.info("C++沙箱程序已关闭...");
        }
    }

    private void monitorProcess() {
        new Thread(() -> {
            try {
                int exitCode = exeProcess.waitFor();  // 阻塞，等待进程退出
                log.info("C++沙箱程序异常退出，退出码：: {}", exitCode);
                // 如果进程退出，尝试重启它
                log.info("重启C++沙箱程序中...");
                startExe();  // 重新启动进程
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * 检查 exe 进程是否存活
     */
    public boolean isExeProcessAlive() {
        if (exeProcess == null) {
            return false;  // 进程未启动
        }
        try {
            // 检查进程是否仍在运行
            int exitCode = exeProcess.exitValue();  // 获取进程的退出码
            return exitCode == 0;  // 如果退出码为 0，表示正常运行
        } catch (IllegalThreadStateException e) {
            // 如果抛出 IllegalThreadStateException，说明进程仍在运行
            return true;
        }
    }
    
    public void addStartListener(Runnable listener) {
        startListeners.add(listener);
    }
}
