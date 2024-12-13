#include "JudgeInfo.h"

// 构造函数
JudgeInfo::JudgeInfo(const std::string& message, const std::string& detail,
                     long time, long memory)
    : message(message), detail(detail), time(time), memory(memory) {}

// Getter 和 Setter 实现
const std::string& JudgeInfo::getMessage() const { return message; }

void JudgeInfo::setMessage(const std::string& message) {
  this->message = message;
}

const std::string& JudgeInfo::getDetail() const { return detail; }

void JudgeInfo::setDetail(const std::string& detail) {
  this->detail = detail;
}

long JudgeInfo::getTime() const { return time; }

void JudgeInfo::setTime(long time) { this->time = time; }

long JudgeInfo::getMemory() const { return memory; }

void JudgeInfo::setMemory(long memory) { this->memory = memory;
}
