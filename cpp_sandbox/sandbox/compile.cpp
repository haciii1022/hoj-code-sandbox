#include <sched.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <unistd.h>  // for getcwd

#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iostream>
#include <sstream>

#include "FileUtils.h"
int compile_user_code(const char* source, const char* output,
                      std::string& error_output) {
  // 创建临时文件保存错误信息
  std::string error_file = FileUtils::getFullPath("compile_errors.txt");
  // 构造 g++ 编译命令
  std::string command = "g++ ";
  command += source;
  command += " -o ";
  command += output;
  command += " 2> ";  // 将错误输出重定向到文件
  command += error_file;

  int compile_status = system(command.c_str());

  // 读取错误文件内容到 error_output
  std::ifstream error_stream(error_file);
  if (error_stream.is_open()) {
    std::ostringstream oss;
    oss << error_stream.rdbuf();
    error_output = oss.str();
    error_stream.close();
  }

  // 删除临时错误文件（可选）
    // std::remove(error_file.c_str());

    return compile_status;
}