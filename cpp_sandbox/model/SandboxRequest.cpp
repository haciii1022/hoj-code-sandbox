// SandboxRequest.cpp
#include "SandboxRequest.h"


SandboxRequest::SandboxRequest(const std::string& json_str) {
    try {
        // 解析 JSON
        json j = json::parse(json_str);

        // 提取字段
        identifier = j["identifier"].get<std::string>();
        inputFilePathList = j["inputFilePathList"].get<std::vector<std::string>>();
        code = j["code"].get<std::string>();
        language = j["language"].get<std::string>();
        timeLimit = j["timeLimit"].get<int>();
        memoryLimit = j["memoryLimit"].get<int>();
    } catch (const std::exception& e) {
        std::cerr << "Failed to parse JSON: " << e.what() << std::endl;
    }
}

void SandboxRequest::print() {
    std::cout << "Code: " << code << std::endl;
    std::cout << "Language: " << language << std::endl;
    std::cout << "Time Limit: " << timeLimit << std::endl;
    std::cout << "Memory Limit: " << memoryLimit << std::endl;
}
