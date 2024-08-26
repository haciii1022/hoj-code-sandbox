package com.mirror.hoj.codesandbox;


import com.mirror.hoj.codesandbox.model.ExecuteCodeRequest;
import com.mirror.hoj.codesandbox.model.ExecuteCodeResponse;

/**
 * @author Mirror
 * @date 2024/7/23
 */
public interface CodeSandbox {
    /**
     * 执行代码
     * 只负责执行代码，记录消耗内存和消耗时间，还有代码的执行结果，其余部分不在此处处理
     * @param executeCodeRequest
     * @return
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}
