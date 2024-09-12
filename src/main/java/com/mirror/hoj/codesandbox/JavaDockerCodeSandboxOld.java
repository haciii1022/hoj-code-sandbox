package com.mirror.hoj.codesandbox;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.mirror.hoj.codesandbox.model.ExecuteCodeRequest;
import com.mirror.hoj.codesandbox.model.ExecuteCodeResponse;
import com.mirror.hoj.codesandbox.model.ExecuteMessage;
import com.mirror.hoj.codesandbox.model.JudgeInfo;
import com.mirror.hoj.codesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Mirror
 * @date 2024/8/13
 */
@Slf4j
public class JavaDockerCodeSandboxOld implements CodeSandbox {
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String SECURITY_MANAGER_CLASS_NAME = "DefaultSecurityManager";

    private static final String SECURITY_MANAGER_PATH = "/home/Mirror/hoj-code-sandbox/src/main/resources/security";

    private static final boolean FIRST_INIT = false;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
//        System.setSecurityManager(new DefaultSecurityManager());
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        //判断全局代码目录是否存在，不存在就创建
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        //把用户代码隔离开存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        //开一个进程Process编译class文件
        String compileCmd = String.format("javac -encoding UTF-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            log.info("编译结果：{}", executeMessage);
        } catch (IOException e) {
            return getErrorResponse(e);
        }

        //创建容器，把文件赋值到容器内
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        String image = "openjdk:8-alpine";
        if (FIRST_INIT) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像：" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("下载镜像失败");
                throw new RuntimeException(e);
            }
            System.out.println("下载完成");
        }
//      FIRST_INIT = false;
        //创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        //挂载文件
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(1024L * 1024 * 1024);
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
        List<JudgeInfo> judgeInfoList = new ArrayList<>();
        List<ExecuteMessage> executeMessageList = new ArrayList<>();

        final String[] message = {null};
        final String[] errorMessage = {null};
        final long[] maxMemory = {0L};
        final boolean[] timeout = {true};
        long totalRunTime = 0L;
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
                        System.out.println("输出错误结果：" + errorMessage[0]);
                    } else {
                        message[0] = new String(frame.getPayload());
                        System.out.println("输出结果：" + message[0]);
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
                    System.out.println("内存占用：" + statistics.getMemoryStats().getUsage());
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
                        .awaitCompletion(executeCodeRequest.getTimeLimit(),TimeUnit.MILLISECONDS);
                stopWatch.stop();
                statsCmd.close();
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            ExecuteMessage executeMessage = new ExecuteMessage();
            long runTime = stopWatch.getLastTaskTimeMillis();
            totalRunTime += runTime;
            executeMessage.setTime(runTime);
            executeMessage.setMemory(maxMemory[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setMessage(message[0]);
            executeMessageList.add(executeMessage);
        }
        long totalMaxMemory = 0L;
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        for (ExecuteMessage executeMessage : executeMessageList) {
            JudgeInfo judgeInfo = new JudgeInfo();
            judgeInfo.setMemory(executeMessage.getMemory());
            judgeInfo.setTime(executeMessage.getTime());
            totalMaxMemory = Math.max(totalMaxMemory,executeMessage.getMemory());
            //TODO judgeInfo 的message要选择 ac/tle/mle/re啥的 或者放在外部获取
            judgeInfo.setMessage(executeMessage.getMessage());
            judgeInfoList.add(judgeInfo);
            //碰到错误就直接返回
            if (StrUtil.isNotBlank(executeMessage.getErrorMessage())) {
                executeCodeResponse.setStatus(3);
                executeCodeResponse.setMessage(executeMessage.getErrorMessage());
                break;
            }
            outputList.add(executeMessage.getMessage());
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo totalJudgeInfo = new JudgeInfo();
        //正常运行执行所有用例
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }
        //TODO totalJudgeInfo不应该在沙箱中实现
        totalJudgeInfo.setTime(totalRunTime / executeMessageList.size());
        totalJudgeInfo.setMemory(totalMaxMemory);
        executeCodeResponse.setOutputList(outputList);
        executeCodeResponse.setJudgeInfoList(judgeInfoList);
        System.out.println("executeCodeResponse: "+executeCodeResponse);
        System.out.println("totalJudgeInfo: "+totalJudgeInfo);
        System.out.println("timeout: "+timeout[0]);
        System.out.println(userCodeFile.getParentFile());
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            log.info("删除" + (del ? "成功" : "失败"));
            System.out.println("删除" + (del ? "成功" : "失败"));
        }
        return executeCodeResponse;
    }

    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 表示代码沙箱错误
        executeCodeResponse.setStatus(2);
        return executeCodeResponse;
    }


    public static void main(String[] args) {
        CodeSandbox codeSandbox = new JavaDockerCodeSandboxOld();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "2 3"));
        executeCodeRequest.setLanguage("java");
        String code = ResourceUtil.readStr("testCode/SimpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        codeSandbox.executeCode(executeCodeRequest);
    }
}
