package com.mirror.hoj.codesandbox;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
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
        //挂载文件
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(MEMORY_LIMIT * 1024);
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
        hostConfig.withReadonlyRootfs(true);// 设置只读根文件系统,不能写
        String profileConfig = ResourceUtil.readUtf8Str("profile.json");
        hostConfig.withSecurityOpts(Arrays.asList("seccomp=" + profileConfig));
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true) //创建一个交互终端
                .exec();
//        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();
        //启动容器
        dockerClient.startContainerCmd(containerId).exec();
        List<ExecuteMessage> executeMessageList = new ArrayList<>();

        final String[] message = {null};
        final String[] errorMessage = {null};
        final long[] maxMemory = {0L};
        final boolean[] timeout = {true};
        for (String inputArgs : inputList) {
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            String execId = execCreateCmdResponse.getId();
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
            // 获取占用的内存
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
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
            ExecuteMessage executeMessage = new ExecuteMessage();
            long runTime = stopWatch.getLastTaskTimeMillis();
            executeMessage.setTime(runTime);
            executeMessage.setMemory(maxMemory[0]);
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

