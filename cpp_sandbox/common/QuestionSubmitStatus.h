//
// Created by root on 24-12-9.
//

#ifndef QUESTION_SUBMIT_STATUS_H
#define QUESTION_SUBMIT_STATUS_H
class QuestionSubmitStatus {
public:
  // 定义静态常量以便直接使用
  static constexpr int WAITING = 0;
  static constexpr int RUNNING = 1;
  static constexpr int SUCCEED = 2;
  static constexpr int FAILED = 3;

  // 根据整数值获取描述性信息（可选）
  static const char* toString(int status) {
    switch (status) {
      case WAITING: return "WAITING";
      case RUNNING: return "RUNNING";
      case SUCCEED: return "SUCCEED";
      case FAILED: return "FAILED";
      default: return "UNKNOWN";
    }
  }
};

#endif
