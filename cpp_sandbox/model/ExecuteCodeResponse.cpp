#include "ExecuteCodeResponse.h"

// 构造函数
ExecuteCodeResponse::ExecuteCodeResponse(
    const std::vector<std::string>& outputList,
    const std::vector<std::string>& outputFilePathList,
    const std::string& message,
    const std::string& detail,
    int status,
    const std::vector<JudgeInfo>& judgeInfoList
) : outputList(outputList),
    outputFilePathList(outputFilePathList),
    message(message),
    detail(detail),
    status(status),
    judgeInfoList(judgeInfoList) {}

// Getter 和 Setter 实现
const std::vector<std::string>& ExecuteCodeResponse::getOutputList() const {
    return outputList;
}

void ExecuteCodeResponse::setOutputList(const std::vector<std::string>& outputList) {
    this->outputList = outputList;
}

const std::vector<std::string>& ExecuteCodeResponse::getOutputFilePathList() const {
    return outputFilePathList;
}

void ExecuteCodeResponse::setOutputFilePathList(const std::vector<std::string>& outputFilePathList) {
    this->outputFilePathList = outputFilePathList;
}

const std::string& ExecuteCodeResponse::getMessage() const {
    return message;
}

void ExecuteCodeResponse::setMessage(const std::string& message) {
    this->message = message;
}

const std::string& ExecuteCodeResponse::getDetail() const {
  return detail;
}

void ExecuteCodeResponse::setDetail(const std::string& detail) {
  this->detail = detail;
}

int ExecuteCodeResponse::getStatus() const {
    return status;
}

void ExecuteCodeResponse::setStatus(int status) {
    this->status = status;
}

const std::vector<JudgeInfo>& ExecuteCodeResponse::getJudgeInfoList() const {
    return judgeInfoList;
}

void ExecuteCodeResponse::setJudgeInfoList(const std::vector<JudgeInfo>& judgeInfoList) {
    this->judgeInfoList = judgeInfoList;
}
