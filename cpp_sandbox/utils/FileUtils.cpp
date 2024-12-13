#include "FileUtils.h"

#include <filesystem>
#include <fstream>
#include <iostream>
// 初始化常量路径
const std::string FileUtils::BASE_PATH = "/home/ubuntu";
const std::string FileUtils::QUESTION_SUBMIT_PREFIX = "hoj/questionSubmit";
const std::string FileUtils::TEMP_DIR =
    FileUtils::BASE_PATH + "/cpp_sandbox/tmpCode";

// 获取完整路径
std::string FileUtils::getFullPath(const std::string& relativePath) {
  return TEMP_DIR + "/" + relativePath;
}

// 保存内容到文件
bool FileUtils::saveToFile(const std::string& filePath,
                           const std::string& content) {
  try {
    // 提取文件的目录部分
    std::filesystem::path dir = std::filesystem::path(filePath).parent_path();

    // 检查目录是否存在
    if (!std::filesystem::exists(dir)) {
      // 如果目录不存在，则创建目录
      if (!std::filesystem::create_directories(dir)) {
        std::cerr << "Error: Unable to create directories for path: " << dir
                  << std::endl;
        return false;
      }
    }

    // 打开文件进行写入
    std::ofstream outFile(filePath);
    if (!outFile) {
      std::cerr << "Error: Unable to open file for writing: " << filePath
                << std::endl;
      return false;
    }

    outFile << content;
    outFile.close();
    return true;
  } catch (const std::exception& e) {
    std::cerr << "Exception while saving file: " << e.what() << std::endl;
    return false;
  }
}

// 从文件读取内容
std::string FileUtils::readFromFile(const std::string& filePath) {
  std::ifstream inFile(filePath);
  if (!inFile) {
    std::cerr << "Error: Unable to open file for reading: " << filePath
              << std::endl;
    return "";
  }
  std::string content((std::istreambuf_iterator<char>(inFile)), std::istreambuf_iterator<char>());
    inFile.close();
    return content;
}

// 确保目录存在，如果目录不存在则创建
bool FileUtils::ensureDirectoryExists(const std::string& fullFilePath) {
  try {
    // 提取文件路径的目录部分
    std::filesystem::path dir = std::filesystem::path(fullFilePath).parent_path();

    // 如果目录不存在，则创建目录
    if (!std::filesystem::exists(dir)) {
      if (!std::filesystem::create_directories(dir)) {
        std::cerr << "Error: Unable to create directory: " << dir << std::endl;
        return false;
      }
    }

    return true;
  } catch (const std::exception& e) {
    std::cerr << "Exception while ensuring directory exists: " << e.what() << std::endl;
    return false;
  }
}
