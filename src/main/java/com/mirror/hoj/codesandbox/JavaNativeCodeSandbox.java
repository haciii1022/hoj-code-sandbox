package com.mirror.hoj.codesandbox;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.dfa.WordTree;
import com.mirror.hoj.codesandbox.model.ExecuteCodeRequest;
import com.mirror.hoj.codesandbox.model.ExecuteCodeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Java沙箱原生实现，直接复用模板方法
 * @author Mirror
 * @date 2024/8/13
 */
@Slf4j
@Component
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate {
    private static final String SECURITY_MANAGER_CLASS_NAME = "DefaultSecurityManager";

    private static final String SECURITY_MANAGER_PATH = "H:\\MyProject\\hoj-code-sandbox\\src\\main\\resources\\security";

    private static final List<String> BlackList = Arrays.asList("Runtime", "Process", "File",
            "FileInputStream", "FileOutputStream", "FileReader", "FileWriter", "Files",
            "URL", "URI", "URLClassLoader", "URLConnection", "HttpURLConnection",
            "DatagramSocket", "ServerSocket", "Socket", "SocketAddress", "SocketException",
            "SocketImpl", "SocketOptions", "SocketTimeoutException", "StreamTokenizer",
            "StringTokenizer", "ZipInputStream", "ZipOutputStream", "ZipFile",
            "ZipEntry", "ZipException", "ZipError", "ZipConstants"
    );
    private static final WordTree WORD_TREE;
    static {
        //初始化字典树
        WORD_TREE = new WordTree();
        //把操作黑名单加入到字典树中
        WORD_TREE.addWords(BlackList);
    }
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String code = executeCodeRequest.getCode();
        //检查代码中是否有黑名单操作
        if (WORD_TREE.isMatch(code)) {
            return getErrorResponse(new RuntimeException("代码中包含黑名单操作"));
        }
        return super.executeCode(executeCodeRequest);
    }

    public static void main(String[] args) {
        CodeSandbox codeSandbox = new JavaNativeCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Collections.singletonList("1 2"));
//        executeCodeRequest.setInputList(Arrays.asList("1 2","3 4"));
        executeCodeRequest.setLanguage("java");
        String code = ResourceUtil.readStr("testCode/SimpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        codeSandbox.executeCode(executeCodeRequest);
    }
}
