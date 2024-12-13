package com.mirror.hoj.codesandbox.controller;


import cn.hutool.core.io.resource.ResourceUtil;
import com.mirror.hoj.codesandbox.CodeSandbox;
import com.mirror.hoj.codesandbox.JavaDockerCodeSandbox;
import com.mirror.hoj.codesandbox.JavaNativeCodeSandbox;
import com.mirror.hoj.codesandbox.client.CppSocketClient;
import com.mirror.hoj.codesandbox.enums.QuestionSubmitLanguageEnum;
import com.mirror.hoj.codesandbox.model.ExecuteCodeRequest;
import com.mirror.hoj.codesandbox.model.ExecuteCodeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

/**
 * @author Mirror
 * @date 2024/8/12
 */
@Slf4j
@RestController("/")
public class MainController {
    // 定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "auth";

    private static final String AUTH_REQUEST_SECRET = "secretKey";

    @Resource
    CppSocketClient cppSocketClient;

    @Resource
    JavaDockerCodeSandbox javaDockerCodeSandbox;

    @Resource
    JavaNativeCodeSandbox javaNativeCodeSandbox;

    @GetMapping("/health")
    public String healthCheck() {
        return "ok";
    }

    /**
     * 执行代码
     *
     * @param executeCodeRequest
     * @return
     */
    @PostMapping("/executeCode")
    ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request,
                                    HttpServletResponse response) {
        // 基本的认证
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(authHeader)) {
            response.setStatus(403);
            return null;
        }
        log.info("代码沙箱执行代码入参: {}", executeCodeRequest);
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        ExecuteCodeResponse result = null;
        if (QuestionSubmitLanguageEnum.JAVA.getValue().equals(executeCodeRequest.getLanguage())) {
            result = javaDockerCodeSandbox.executeCode(executeCodeRequest);
        } else if (QuestionSubmitLanguageEnum.CPP.getValue().equals(executeCodeRequest.getLanguage())) {
            result = cppSocketClient.executeCode(executeCodeRequest);
        }
        return result;
    }

    @GetMapping("/test")
    public ExecuteCodeResponse test(HttpServletRequest request) {
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setIdentifier(UUID.randomUUID().toString());
//        executeCodeRequest.setInputList(Collections.singletonList("1 2"));
        executeCodeRequest.setInputFilePathList(Collections.singletonList("/home/ubuntu/hoj/question/1801181035134369793/4_0.in"));
//        executeCodeRequest.setInputList(Arrays.asList("1 2","3 4"));
        executeCodeRequest.setLanguage("cpp");
        String code = ResourceUtil.readStr("testCode/SimpleComputeArgs/test.cpp", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setTimeLimit(10000L);
        executeCodeRequest.setMemoryLimit(256L);
        ExecuteCodeResponse res = cppSocketClient.executeCode(executeCodeRequest);
//        ExecuteCodeResponse res2 = cppSocketClient.executeCode(executeCodeRequest);
//        ExecuteCodeResponse res3 = cppSocketClient.executeCode(executeCodeRequest);
        System.out.println(res);
        return res;
    }
}
