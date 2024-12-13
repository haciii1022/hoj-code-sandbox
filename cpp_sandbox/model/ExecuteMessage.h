#ifndef EXECUTE_MESSAGE_H
#define EXECUTE_MESSAGE_H

#include <nlohmann/json.hpp>
#include <string>
class ExecuteMessage {
 public:
  // 构造函数
  ExecuteMessage() = default;

  // Getter 和 Setter
  int getExitValue() const;
  void setExitValue(int exitValue);

  const std::string& getMessage() const;
  void setMessage(const std::string& message);

  const std::string& getOutputFilePath() const;
  void setOutputFilePath(const std::string& outputFilePath);

  const std::string& getErrorMessage() const;
  void setErrorMessage(const std::string& errorMessage);

  long getTime() const;
  void setTime(long time);

  long getMemory() const;
  void setMemory(long memory);
  // 使 ExecuteMessage 类支持 JSON 序列化
  NLOHMANN_DEFINE_TYPE_INTRUSIVE(ExecuteMessage, exitValue, message,
                                 outputFilePath, errorMessage, time, memory)
 private:
  int exitValue = 0;
  std::string message; // 弃用，现输出内容保存到文件
  std::string outputFilePath;
  std::string errorMessage;
  long time = 0;
  long memory = 0;
};

#endif // EXECUTE_MESSAGE_H
