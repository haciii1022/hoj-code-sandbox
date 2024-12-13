#include "ExecuteMessage.h"

// Getter 和 Setter 实现
int ExecuteMessage::getExitValue() const {
    return exitValue;
}

void ExecuteMessage::setExitValue(int exitValue) {
    this->exitValue = exitValue;
}

const std::string& ExecuteMessage::getMessage() const {
    return message;
}

void ExecuteMessage::setMessage(const std::string& message) {
    this->message = message;
}

const std::string& ExecuteMessage::getOutputFilePath() const {
    return outputFilePath;
}

void ExecuteMessage::setOutputFilePath(const std::string& outputFilePath) {
    this->outputFilePath = outputFilePath;
}

const std::string& ExecuteMessage::getErrorMessage() const {
    return errorMessage;
}

void ExecuteMessage::setErrorMessage(const std::string& errorMessage) {
    this->errorMessage = errorMessage;
}

long ExecuteMessage::getTime() const {
    return time;
}

void ExecuteMessage::setTime(long time) {
    this->time = time;
}

long ExecuteMessage::getMemory() const {
    return memory;
}

void ExecuteMessage::setMemory(long memory) {
    this->memory = memory;
}
