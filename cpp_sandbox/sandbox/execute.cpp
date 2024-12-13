#include <fcntl.h>
#include <signal.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include <chrono>
#include <cmath>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <string>

#include "ExecuteMessage.h"
#include "FileUtils.h"
constexpr long long fixed_overhead_kb = 2176;
void alarm_handler(int sig) {
  // std::cout << "Time limit exceeded, program terminated by SIGALRM!"
  //           << std::endl;
  exit(1);  // 终止程序
}
ExecuteMessage execute_user_code(const char* executable,
                                 const char* input_file_path,
                                 const char* user_output_file_path,
                                 const long time_out) {
  // 创建子进程
  ExecuteMessage message;
  message.setExitValue(0);
  pid_t pid = fork();
  if (pid == 0) {
    signal(SIGALRM, alarm_handler);  // 注册 SIGALRM 信号的处理函数
    alarm(static_cast<unsigned int>(
        std::ceil(static_cast<double>(time_out) / 1000) + 1));  // 设置定时器
    // 设置定时器
    // 打开输入文件（重定向 stdin）
    int input_fd = open(input_file_path, O_RDONLY);
    if (input_fd < 0) {
      perror("open input file");
      exit(1);
    }
    FileUtils::ensureDirectoryExists(user_output_file_path);
    // 打开输出文件（重定向 stdout 和 stderr）
    int output_fd =
        open(user_output_file_path, O_WRONLY | O_CREAT | O_TRUNC, 0664);
    int error_fd =
        open(user_output_file_path, O_WRONLY | O_CREAT | O_TRUNC, 0664);
    if (output_fd < 0 || error_fd < 0) {
      perror("open output file");
      exit(1);
    }

    // 重定向标准输入/输出/错误
    dup2(input_fd, STDIN_FILENO);
    dup2(output_fd, STDOUT_FILENO);
    dup2(error_fd, STDERR_FILENO);
    close(input_fd);
    close(output_fd);
    close(error_fd);
    // 执行用户程序
    execl(executable, executable, (char*)NULL);

    // 如果执行失败
    perror("execl failed");
    exit(1);
  } else if (pid > 0) {
    auto start_time = std::chrono::high_resolution_clock::now();

    // 等待子进程完成
    int status;
    rusage usage;
    wait4(pid, &status, 0, &usage);

    auto end_time = std::chrono::high_resolution_clock::now();
    std::chrono::duration<double> elapsed = end_time - start_time;

    // 打印执行时间
    // std::cout << "Execution time: " << elapsed.count() << " seconds"
    //           << std::endl;
    message.setTime(elapsed.count() * 1000);
    message.setMemory(usage.ru_maxrss - fixed_overhead_kb);
    message.setOutputFilePath(user_output_file_path);
    // 打印内存使用情况
    // std::cout << "Maximum memory used: " << usage.ru_maxrss << " KB"
    //           << std::endl;
    // 检查子进程退出状态
    if (WIFEXITED(status)) {
      // std::cout << "Child exited with code: " << WEXITSTATUS(status)
      //           << std::endl;
    } else if (WIFSIGNALED(status)) {
      int signal_number = WTERMSIG(status);  // 获取终止信号的编号
      const char* signal_description =
          strsignal(signal_number);  // 获取信号的描述
      // std::cout << "Child killed by signal: " << signal_number << std::endl;
      message.setErrorMessage(signal_description);
    }
    message.setExitValue(status);
  } else {
    // fork 失败
    perror("fork failed");
    exit(1);
  }
  return message;
}