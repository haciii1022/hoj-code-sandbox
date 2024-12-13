//
// Created by root on 24-12-9.
//

#ifndef JUDGE_INFO_MESSAGE_H
#define JUDGE_INFO_MESSAGE_H
#include <string>

class JudgeInfoMessage {
public:
  // 定义静态常量字符串
  static const std::string ACCEPTED;
  static const std::string WRONG_ANSWER;
  static const std::string COMPILE_ERROR;
  static const std::string MEMORY_LIMIT_EXCEEDED;
  static const std::string TIME_LIMIT_EXCEEDED;
  static const std::string PRESENTATION_ERROR;
  static const std::string OUTPUT_LIMIT_EXCEEDED;
  static const std::string WAITING;
  static const std::string DANGEROUS_OPERATION;
  static const std::string RUNTIME_ERROR;
  static const std::string SYSTEM_ERROR;
};

// 在类外初始化静态常量
const std::string JudgeInfoMessage::ACCEPTED = "Accepted";
const std::string JudgeInfoMessage::WRONG_ANSWER = "Wrong Answer";
const std::string JudgeInfoMessage::COMPILE_ERROR = "Compile Error";
const std::string JudgeInfoMessage::MEMORY_LIMIT_EXCEEDED = "Memory Limit Exceeded";
const std::string JudgeInfoMessage::TIME_LIMIT_EXCEEDED = "Time Limit Exceeded";
const std::string JudgeInfoMessage::PRESENTATION_ERROR = "Presentation Error";
const std::string JudgeInfoMessage::OUTPUT_LIMIT_EXCEEDED = "Output Limit Exceeded";
const std::string JudgeInfoMessage::WAITING = "Waiting";
const std::string JudgeInfoMessage::DANGEROUS_OPERATION = "Dangerous Operation";
const std::string JudgeInfoMessage::RUNTIME_ERROR = "Runtime Error";
const std::string JudgeInfoMessage::SYSTEM_ERROR = "System Error";


#endif
