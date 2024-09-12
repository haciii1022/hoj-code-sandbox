package com.mirror.hoj.codesandbox;

import cn.hutool.core.io.FileUtil;
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
import java.util.List;
import java.util.UUID;

@Slf4j
public abstract class JavaCodeSandboxTemplate implements CodeSandbox {
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static long TIME_OUT = 5000L;


    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        log.info("executeCode start");
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        TIME_OUT = executeCodeRequest.getTimeLimit();
        try {
            //1、把用户代码保存为文件
            File userCodeFile = saveUserCodeToFile(code);
            //2、编译代码，得到class文件
            ExecuteMessage compileFileExecuteMessage = compileFile(userCodeFile);
            log.info("编译结果：{}", compileFileExecuteMessage);
            //3、依次执行输入用例，获取执行结果列表
            List<ExecuteMessage> executeMessageList = runFile(userCodeFile, inputList);
            //4、获取返回相应
            ExecuteCodeResponse executeCodeResponse = getOutPutResponse(executeMessageList);
            //5、清理文件
            boolean deleted = deleteFile(userCodeFile);
            if (!deleted) {
                log.error("deleteFile error, userCodeFilePath = {}", userCodeFile.getAbsolutePath());
            }
            return executeCodeResponse;
        }catch (Exception e){
            log.error("executeCode error", e);
            return getErrorResponse(e);
        }
    }

    /**
     * 1、把用户代码保存为文件
     *
     * @param code 用户代码
     * @return
     */
    public File saveUserCodeToFile(String code) {

        //检查代码中是否有黑名单操作
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        System.out.println(globalCodePathName);
        //把用户代码隔离开存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        log.info("保存用户代码文件：{}", userCodeFile.getAbsolutePath());
        return userCodeFile;
    }

    /**
     * 2、编译代码，得到class文件
     *
     * @param userCodeFile
     * @return
     */
    public ExecuteMessage compileFile(File userCodeFile) {
        log.info("开始编译文件: {}", userCodeFile.getAbsolutePath());
        //开一个进程Process编译class文件
        String compileCmd = String.format("javac -encoding UTF-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            if (executeMessage.getExitValue() != 0) {
                throw new RuntimeException("编译错误");
            }
            log.info("编译成功");
            return executeMessage;
        } catch (IOException e) {
            throw new RuntimeException(e);
//            return getErrorResponse(e);
        }
    }

    /**
     * 3、依次执行输入用例，获取执行结果列表
     *
     * @param userCodeFile
     * @param inputList
     * @return
     */
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        //记录一下总耗时
        long totalRunTime = 0L;
        //运行class文件，获取结果
        for (String input : inputList) {
            String runCmd = String.format("java -Xmx128m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, input);
//            String runCmd = String.format("java -Xmx128m -Dfile.encoding=UTF-8 -cp %s%s%s -Djava.security.manager=%s Main %s", userCodeParentPath, File.pathSeparator, SECURITY_MANAGER_PATH, SECURITY_MANAGER_CLASS_NAME, input);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        log.info("超时了，中断程序");
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }


                }).start();
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                log.info("运行结果：{}", executeMessage);
                long runTime = executeMessage.getTime();
                totalRunTime += runTime;
                executeMessageList.add(executeMessage);
            } catch (IOException e) {
                throw new RuntimeException("执行错误", e);
//                return getErrorResponse(e);
            }
        }
        return executeMessageList;
    }

    /**
     * 4、获取返回相应
     *
     * @param executeMessageList
     * @return
     */
    public ExecuteCodeResponse getOutPutResponse(List<ExecuteMessage> executeMessageList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        List<JudgeInfo> judgeInfoList = new ArrayList<>();
        long totalRunTime = 0L;
        long maxMemory = 0L;
        for (ExecuteMessage executeMessage : executeMessageList) {
            JudgeInfo judgeInfo = new JudgeInfo();
            judgeInfo.setTime(executeMessage.getTime());
            totalRunTime += executeMessage.getTime();
            judgeInfo.setMemory(executeMessage.getMemory());
            maxMemory = Math.max(maxMemory, executeMessage.getMemory());
            judgeInfoList.add(judgeInfo);
            outputList.add(executeMessage.getMessage());
            //碰到错误就直接返回
            if (StrUtil.isNotBlank(executeMessage.getErrorMessage())) {
                executeCodeResponse.setStatus(3);
                executeCodeResponse.setMessage(executeMessage.getErrorMessage());
                break;
            }
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo totalJudgeInfo = new JudgeInfo();
        //正常运行执行所有用例
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }
        totalJudgeInfo.setTime(totalRunTime / executeMessageList.size());
        totalJudgeInfo.setMemory(maxMemory);
        executeCodeResponse.setJudgeInfoList(judgeInfoList);
        log.info("获取输出响应: {}",executeCodeResponse);
        return executeCodeResponse;
    }

    /**
     * 5、清理文件
     *
     * @param userCodeFile
     * @return
     */
    public boolean deleteFile(File userCodeFile) {
        boolean del = false;
        if (userCodeFile.getParentFile() != null) {
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            del = FileUtil.del(userCodeParentPath);
            log.info("删除" + (del ? "成功" : "失败"));
        }
        return del;
    }


    /**
     * 6、获取错误响应
     *
     * @param e
     * @return
     */
    public ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 表示代码沙箱错误
        executeCodeResponse.setStatus(2);
        return executeCodeResponse;
    }
}
