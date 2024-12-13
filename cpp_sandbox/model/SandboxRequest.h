#ifndef SANDBOX_REQUEST_H
#define SANDBOX_REQUEST_H
// SandboxRequest.h
#include <nlohmann/json.hpp>
#include <vector>
#include <string>
#include <iostream>
using json = nlohmann::json;

class SandboxRequest {
public:
    // 类成员
    std::string identifier;
    std::vector<std::string> inputFilePathList;
    std::string code;
    std::string language;
    int timeLimit;
    int memoryLimit;

    // 构造函数声明
    SandboxRequest(const std::string& json_str);

    // 打印请求内容
    void print();

    // 支持 JSON 序列化
    NLOHMANN_DEFINE_TYPE_INTRUSIVE(SandboxRequest,
        identifier,
        inputFilePathList,
        code,
        language,
        timeLimit,
        memoryLimit)
};
#endif