package com.mirror.hoj.codesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.mirror.hoj.codesandbox.model.ExecuteCodeRequest;
import com.mirror.hoj.codesandbox.model.ExecuteCodeResponse;
import com.mirror.hoj.codesandbox.model.ExecuteMessage;
import com.mirror.hoj.codesandbox.model.JudgeInfo;
import com.mirror.hoj.codesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * @author Mirror
 * @date 2024/8/13
 */
@Slf4j
public class JavaNativeCodeSandbox implements CodeSandbox {
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
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
        }
        catch (IOException e) {
            return getErrorResponse(e);
        }

        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        //记录一下总耗时
        Long totalRunTime = 0L;
        List<JudgeInfo> judgeInfoList = new ArrayList<>();
        //运行class文件，获取结果
        for (String input : inputList) {
            String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, input);
            Process runProcess = null;
            try {

                runProcess = Runtime.getRuntime().exec(runCmd);
                //TODO 如果超时，直接返回，然后记录一下是第几个超时
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                log.info("运行结果：{}", executeMessage);
                long runTime = executeMessage.getTime();
                totalRunTime += runTime;
                //TODO 处理一下内存还有message
                JudgeInfo judgeInfo = new JudgeInfo();
                judgeInfo.setTime(runTime);
                judgeInfo.setMemory(0L);
                judgeInfoList.add(judgeInfo);
                executeMessageList.add(executeMessage);
            }
            catch (IOException e) {
                return getErrorResponse(e);
            }
        }
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        for(ExecuteMessage executeMessage : executeMessageList){
            //碰到错误就直接返回
            if(StrUtil.isNotBlank(executeMessage.getErrorMessage())){
                executeCodeResponse.setStatus(3);
                executeCodeResponse.setMessage(executeMessage.getErrorMessage());
                break;
            }
            outputList.add(executeMessage.getMessage());
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo totalJudgeInfo = new JudgeInfo();
        //正常运行执行所有用例
        if(outputList.size() == executeMessageList.size()){
            executeCodeResponse.setStatus(1);
        }
        totalJudgeInfo.setTime(totalRunTime/executeMessageList.size());
        totalJudgeInfo.setMemory(0L);
        executeCodeResponse.setJudgeInfoList(judgeInfoList);

        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            log.info("删除" + (del ? "成功" : "失败"));
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
        CodeSandbox codeSandbox = new JavaNativeCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "2 3"));
        executeCodeRequest.setLanguage("java");
        String code = ResourceUtil.readStr("testCode/SimpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        codeSandbox.executeCode(executeCodeRequest);
    }
}
