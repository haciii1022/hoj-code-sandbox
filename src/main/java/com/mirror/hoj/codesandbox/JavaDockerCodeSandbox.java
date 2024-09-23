package com.mirror.hoj.codesandbox;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.mirror.hoj.codesandbox.model.ExecuteCodeRequest;
import com.mirror.hoj.codesandbox.model.ExecuteCodeResponse;
import com.mirror.hoj.codesandbox.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {
    private static final boolean FIRST_INIT = false;
    private static long TIME_LIMIT = 5000L; //单位ms
    private static long MEMORY_LIMIT = 5000L; //单位kb
    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        //创建容器，把文件赋值到容器内
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        log.info("dockerClient: {}", dockerClient);
        String image = "openjdk:8-alpine";
        if (FIRST_INIT) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                   log.info("下载镜像：{}", item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                log.error("下载镜像失败");
                throw new RuntimeException(e);
            }
            log.info("下载完成");
        }
//      FIRST_INIT = false;
        //创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        log.info("创建容器：{}", containerCmd);
        //挂载文件
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(MEMORY_LIMIT * 1024);
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
        hostConfig.withReadonlyRootfs(true);// 设置只读根文件系统,不能写
        log.info("hostConfig: {}", hostConfig);
        log.info("开始读取profile.json");
        String profileConfig = ResourceUtil.readUtf8Str("profile.json");
//        String profileConfig = ResourceUtil.readUtf8Str("/home/Mirror/hoj-code-sandbox/src/main/resources/profile.json");
        log.info("profileConfig: {}", profileConfig);
        hostConfig.withSecurityOpts(Arrays.asList("seccomp=" + profileConfig));
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        log.info("hostConfig: {}", hostConfig);
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true) //创建一个交互终端
                .exec();
        String containerId = createContainerResponse.getId();
        log.info("创建容器：{}", containerId);
        //启动容器
        dockerClient.startContainerCmd(containerId).exec();
        log.info("启动容器：{}", containerId);
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        final String[] message = {null};
        final String[] errorMessage = {null};
        final long[] maxMemory = {0L};
        final boolean[] timeout = {true};
        for (String inputArgs : inputList) {
            ExecuteMessage executeMessage = new ExecuteMessage();
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            String execId = execCreateCmdResponse.getId();
            log.info("execId: {}", execId);
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        errorMessage[0] = new String(frame.getPayload());
                        log.info("输出错误结果：{}", errorMessage[0]);
                    } else {
                        message[0] = new String(frame.getPayload());
                        log.info("输出结果：{}", message[0]);
                    }
                    super.onNext(frame);
                }
                @Override
                public void onComplete() {
                    timeout[0] = false;
                    super.onComplete();
                }
            };
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            // 获取占用的内存
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
                    log.info("获取内存使用情况：{}", maxMemory[0]);
                }

                @Override
                public void close() throws IOException {

                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {
                    executeMessage.setMemory(maxMemory[0]);
//                    latch.countDown(); // 通知执行完成
                }
            });
            statsCmd.exec(statisticsResultCallback);
            StopWatch stopWatch = new StopWatch();
            try {
                stopWatch.start();
                dockerClient.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_LIMIT, TimeUnit.MILLISECONDS);
                stopWatch.stop();
                statsCmd.close();
            } catch (InterruptedException e) {
                log.error("程序执行异常");
                throw new RuntimeException(e);
            }
            long runTime = stopWatch.getLastTaskTimeMillis();
            executeMessage.setTime(runTime);

            try {
                Thread.sleep(200);
//                latch.await(); // 等待执行完成
                statisticsResultCallback.onComplete();
            } catch (InterruptedException e) {
                executeMessage.setMemory(maxMemory[0]);
            }
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setMessage(message[0]);
            executeMessageList.add(executeMessage);
        }
        log.info("执行输入用例，获取执行结果列表出参: {}",executeMessageList);
        return executeMessageList;
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        TIME_LIMIT = executeCodeRequest.getTimeLimit();
        MEMORY_LIMIT = executeCodeRequest.getMemoryLimit();
        return super.executeCode(executeCodeRequest);
    }

    public static void main(String[] args) {
        CodeSandbox codeSandbox = new JavaDockerCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "2 3"));
        executeCodeRequest.setLanguage("java");
        executeCodeRequest.setTimeLimit(1000L);
        executeCodeRequest.setMemoryLimit(1000L*128);
        String code = ResourceUtil.readStr("testCode/SimpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        codeSandbox.executeCode(executeCodeRequest);
    }
}

