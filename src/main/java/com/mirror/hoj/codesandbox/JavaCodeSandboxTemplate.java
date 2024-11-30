package com.mirror.hoj.codesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.mirror.hoj.codesandbox.enums.JudgeInfoMessageEnum;
import com.mirror.hoj.codesandbox.enums.QuestionSubmitStatusEnum;
import com.mirror.hoj.codesandbox.model.ExecuteCodeRequest;
import com.mirror.hoj.codesandbox.model.ExecuteCodeResponse;
import com.mirror.hoj.codesandbox.model.ExecuteMessage;
import com.mirror.hoj.codesandbox.model.JudgeInfo;
import com.mirror.hoj.codesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
public abstract class JavaCodeSandboxTemplate implements CodeSandbox {
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static long TIME_OUT = 5000L;

    // 定义文件权限 755
    private static final Set<PosixFilePermission> PERMISSIONS_755 = PosixFilePermissions.fromString("rwxr-xr-x");

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        log.info("executeCode start");
        List<String> inputList = executeCodeRequest.getInputList();
        List<String> inputFilePathList = executeCodeRequest.getInputFilePathList();
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
            List<ExecuteMessage> executeMessageList = runFile(userCodeFile, inputFilePathList);
            //4、获取返回相应
            ExecuteCodeResponse executeCodeResponse = getOutPutResponse(executeMessageList);
            //5、清理文件
//            boolean deleted = deleteFile(userCodeFile);
//            if (!deleted) {
//                log.error("deleteFile error, userCodeFilePath = {}", userCodeFile.getAbsolutePath());
//            }
            return executeCodeResponse;
        } catch (Exception e) {
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
//        String compileCmd = String.format("javac -encoding UTF-8 %s", userCodeFile.getAbsolutePath());
        String compileCmd = String.format("/www/server/java/jdk1.8.0_371/bin/javac -encoding UTF-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            if (executeMessage.getExitValue() != 0) {
                throw new RuntimeException(JudgeInfoMessageEnum.COMPILE_ERROR.getText());
            }
            log.info("编译成功");
            return executeMessage;
        } catch (IOException e) {
            throw new RuntimeException(JudgeInfoMessageEnum.COMPILE_ERROR.getText());
//            return getErrorResponse(e);
        }
    }

    /**
     * 3、依次执行输入用例，获取执行结果列表
     *
     * @param userCodeFile
     * @param inputFilePathList
     * @return
     */
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputFilePathList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        //记录一下总耗时
        long totalRunTime = 0L;
        //运行class文件，获取结果
//        String inputFilePath = "/home/ubuntu/hoj/question/1801181035134369793/4_0.in";


        String localUserOutputFilePath = userCodeParentPath + File.separator + "tmp.ans";
        String localInputFilePath = userCodeParentPath + File.separator + "tmp.in";

        File localInputFile = createFileWithPermissions(localInputFilePath);
        File localUserOutputFile = createFileWithPermissions(localUserOutputFilePath);
        File localErrorFile = createFileWithPermissions("error.log");

        for (String inputFilePath : inputFilePathList) {
            String inputFileName = Paths.get(inputFilePath).getFileName().toString();
            String folderPath = Paths.get(inputFilePath).getParent().toString();
            String prefix = inputFileName.split("_")[0];
            String userOutputFilePath = userCodeParentPath + File.separator + prefix + "_3.ans";

            Resource resource = com.mirror.hoj.codesandbox.utils.FileUtil.downloadFileViaSFTP(inputFilePath);
            com.mirror.hoj.codesandbox.utils.FileUtil.resourceToFile(resource, localInputFile);
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "/www/server/java/jdk1.8.0_371/bin/java", "-Dfile.encoding=UTF-8", "-cp", userCodeParentPath, "Main"
            );
            processBuilder.redirectInput(localInputFile);
            processBuilder.redirectOutput(localUserOutputFile);
            processBuilder.redirectError(localErrorFile);
//            String runCmd = String.format("/www/server/java/jdk1.8.0_371/bin/java -Xmx128m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, input);
//            String runCmd = String.format("java -Xmx128m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, input);
//            String runCmd = String.format("java -Xmx128m -Dfile.encoding=UTF-8 -cp %s%s%s -Djava.security.manager=%s Main %s", userCodeParentPath, File.pathSeparator, SECURITY_MANAGER_PATH, SECURITY_MANAGER_CLASS_NAME, input);
            try {
                // 启动进程
                Process runProcess = processBuilder.start();
//                Process runProcess = Runtime.getRuntime().exec(runCmd);
//                new Thread(() -> {
//                    try {
//                        Thread.sleep(TIME_OUT);
//                        runProcess.destroy();
//                        log.info("超时了，中断程序");
//                    } catch (InterruptedException e) {
//                        throw new RuntimeException(e);
//                    }
//
//                }).start();
                // 启动线程处理标准输出流和错误流，防止阻塞
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                log.info("保存用户输出文件：{}", userOutputFilePath);
                com.mirror.hoj.codesandbox.utils.FileUtil.saveFileViaSFTP(createFileWithPermissions(localUserOutputFilePath), userOutputFilePath);
                executeMessage.setOutputFilePath(userOutputFilePath);
                log.info("运行结果：{}", executeMessage);
                long runTime = executeMessage.getTime();
                totalRunTime += runTime;
                executeMessageList.add(executeMessage);
            } catch (Exception e) {
                throw new RuntimeException("执行错误: " + e.getMessage(), e);
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
        executeCodeResponse.setStatus(QuestionSubmitStatusEnum.SUCCEED.getValue());
        List<String> outputList = new ArrayList<>();
        List<String> outputFilePathList = new ArrayList<>();
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
            outputFilePathList.add(executeMessage.getOutputFilePath());
            //碰到错误就直接返回
            if (StrUtil.isNotBlank(executeMessage.getErrorMessage())) {
                executeCodeResponse.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
                executeCodeResponse.setMessage(executeMessage.getErrorMessage());
//                break;
            }
        }
        executeCodeResponse.setOutputList(outputList);
        executeCodeResponse.setOutputFilePathList(outputFilePathList);
        JudgeInfo totalJudgeInfo = new JudgeInfo();
//        //正常运行执行所有用例
//        if (outputList.size() == executeMessageList.size()) {
//            executeCodeResponse.setStatus(QuestionSubmitStatusEnum.SUCCEED.getValue());
//        }
        totalJudgeInfo.setTime(totalRunTime / executeMessageList.size());
        totalJudgeInfo.setMemory(maxMemory);
        executeCodeResponse.setJudgeInfoList(judgeInfoList);
        log.info("获取输出响应: {}", executeCodeResponse);
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
        executeCodeResponse.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
        return executeCodeResponse;
    }

    // 自定义方法来创建文件并设置权限为 755
    protected File createFileWithPermissions(String path) {
        File file = new File(path);
        try {
            // 如果文件不存在，则创建文件
            if (!file.exists()) {
                file.createNewFile();
                Files.setPosixFilePermissions(Paths.get(path), PERMISSIONS_755);
                log.info("文件路径为 {} 的文件权限已成功设置为 rwxr-xr-x", path);
            }
        } catch (IOException e) {
            log.error("创建文件失败: {}", e.getMessage(), e);
            throw new RuntimeException("创建文件失败：" + e.getMessage());
        }
        return file;
    }
}
