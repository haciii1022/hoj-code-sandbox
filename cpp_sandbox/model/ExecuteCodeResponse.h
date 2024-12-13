#ifndef EXECUTE_CODE_RESPONSE_H
#define EXECUTE_CODE_RESPONSE_H

#include <string>
#include <vector>
#include "JudgeInfo.h"
#include "nlohmann/json.hpp"

class ExecuteCodeResponse {
public:
    // 构造函数
    ExecuteCodeResponse() = default;
    ExecuteCodeResponse(
        const std::vector<std::string>& outputList,
        const std::vector<std::string>& outputFilePathList,
        const std::string& message,
        const std::string& detail,
        int status,
        const std::vector<JudgeInfo>& judgeInfoList
    );

    // Getter 和 Setter
    const std::vector<std::string>& getOutputList() const;
    void setOutputList(const std::vector<std::string>& outputList);

    const std::vector<std::string>& getOutputFilePathList() const;
    void setOutputFilePathList(const std::vector<std::string>& outputFilePathList);

    const std::string& getMessage() const;
    void setMessage(const std::string& message);

    const std::string& getDetail() const;
    void setDetail(const std::string& detail);

    int getStatus() const;
    void setStatus(int status);

    const std::vector<JudgeInfo>& getJudgeInfoList() const;
    void setJudgeInfoList(const std::vector<JudgeInfo>& judgeInfoList);

    // 支持 JSON 序列化
    NLOHMANN_DEFINE_TYPE_INTRUSIVE(ExecuteCodeResponse,
        outputList,
        outputFilePathList,
        message,
        detail,
        status,
        judgeInfoList)

private:
    std::vector<std::string> outputList;
    std::vector<std::string> outputFilePathList;
    std::string message;
    std::string detail;
    int status = 0;
    std::vector<JudgeInfo> judgeInfoList;
};

#endif // EXECUTE_CODE_RESPONSE_H
