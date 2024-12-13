#include <ExecuteCodeResponse.h>
#include <netinet/in.h>
#include <sched.h>
#include <sys/epoll.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include <algorithm>
#include <csignal>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <nlohmann/json.hpp>
#include <thread>

#include "FileUtils.h"
#include "JudgeInfoMessage.h"
#include "NamespaceUtils.h"
#include "QuestionSubmitStatus.h"
#include "SandboxRequest.h"
#include "cgroup.h"
#include "compile.h"
#include "execute.h"
#define MAX_EVENTS 10
constexpr int PORT = 8091;                  // 服务端监听端口
constexpr int BUFFER_SIZE = 1024 * 16;      // 接收数据的缓冲区大小
constexpr size_t STACK_SIZE = 1024 * 1024;  // 1MB 栈空间
int cnt = 0;
int tmp_id;
// 定义参数结构体
struct ChildArgs {
  SandboxRequest* request;
  int client_socket;
};
void log(const std::string& message, bool is_error = false) {
  if (is_error) {
    std::cerr << "[ERROR]: " << message << std::endl;
  } else {
    std::cout << "[INFO]: " << message << std::endl;
  }
}
// 隐藏文件路径，保留文件名和错误信息
std::string hide_file_path(const std::string& error_output,
                           const std::string& path_to_hide) {
  std::string result = error_output;
  size_t pos = 0;

  // 查找并替换路径
  while ((pos = result.find(path_to_hide, pos)) != std::string::npos) {
    result.erase(pos,
                 path_to_hide.length());  // 从pos位置删除path_to_hide的长度
  }

  return result;
}

void handle_exit_status(JudgeInfo& judge_info, const int exit_value) {
  if (__WIFEXITED(exit_value)) {              // 检查子进程是否正常退出
    int exit_code = WEXITSTATUS(exit_value);  // 获取退出码
    // 根据 exit_code 设置执行结果信息
    if (exit_code == 0) {
      // judge_info.setMessage(JudgeInfoMessage::ACCEPTED);  // 正常结束
      // judge_info.setDetail("Exit code: " + std::to_string(exit_code));
    } else {
      judge_info.setMessage(JudgeInfoMessage::RUNTIME_ERROR);  // 错误退出
      judge_info.setDetail("Exit code: " + std::to_string(exit_code));
    }
  } else if (WIFSIGNALED(exit_value)) {      // 检查子进程是否被信号终止
    int signal_code = WTERMSIG(exit_value);  // 获取终止信号编号
    // 根据 signal_code 设置执行结果信息
    if (signal_code == SIGSEGV) {  // 段错误
      judge_info.setMessage(JudgeInfoMessage::RUNTIME_ERROR);
      judge_info.setDetail(
          "Segmentation Fault (Signal: " + std::to_string(signal_code) + ")");
    } else if (signal_code == SIGFPE) {  // 除零错误
      judge_info.setMessage(JudgeInfoMessage::RUNTIME_ERROR);
      judge_info.setDetail("Floating Point Exception (Signal: " +
                           std::to_string(signal_code) + ")");
    } else if (signal_code == SIGALRM) {  // 超时
      judge_info.setMessage(JudgeInfoMessage::TIME_LIMIT_EXCEEDED);
      judge_info.setDetail(
          "Execution Timeout (Signal: " + std::to_string(signal_code) + ")");
    } else {  // 其他运行时错误
      judge_info.setMessage(JudgeInfoMessage::RUNTIME_ERROR);
      judge_info.setDetail(
          "Runtime Error (Signal: " + std::to_string(signal_code) + ")");
    }
  } else {  // 如果既不是正常退出也没有被信号终止
    judge_info.setMessage(JudgeInfoMessage::SYSTEM_ERROR);
    judge_info.setDetail("Unknown termination state (Exit value: " +
                         std::to_string(exit_value) + ")");
  }
}
void send_message_to_client(const int client_socket,
                            const std::string& message) {
  const std::string format_message = message + "\n";
  log("socketID " + std::to_string(client_socket) +
      " send message to client: " + format_message);
  send(client_socket, format_message.c_str(), format_message.size(), 0);
}
void send_message_to_client(const int client_socket,
                            const ExecuteCodeResponse& message) {
  const std::string json_message = nlohmann::json(message).dump(-1) + "\n";
  log("socketID " + std::to_string(client_socket) +
      " send message to client: " + json_message);
  send(client_socket, json_message.c_str(), json_message.size(), 0);
}
// 打印错误信息并关闭连接
void close_connection(const int client_socket, const std::string& error_msg) {
  log("socketID " + std::to_string(client_socket) + " : " + error_msg, true);
  send_message_to_client(client_socket, error_msg);
  close(client_socket);
}
// 子进程执行逻辑
int child_fn(void* arg) {
  auto* child_args = static_cast<ChildArgs*>(arg);
  SandboxRequest* request = child_args->request;
  int client_socket = child_args->client_socket;
  std::string code = request->code;
  const long time_out = request->timeLimit;
  const long memory_limit = request->memoryLimit;
  std::string identifier = request->identifier;

  std::string user_code_path = FileUtils::getFullPath(identifier + "/main.cpp");
  std::string user_code_exec = FileUtils::getFullPath(identifier + "/main.exe");
  log("socketID " + std::to_string(client_socket) +
      " userCodePath: " + user_code_path);
  FileUtils::saveToFile(user_code_path, code);

  ExecuteCodeResponse response;
  // 编译代码
  std::string error_output;
  if (compile_user_code(user_code_path.c_str(), user_code_exec.c_str(),
                        error_output) != 0) {
    response.setStatus(QuestionSubmitStatus::FAILED);
    response.setMessage(JudgeInfoMessage::COMPILE_ERROR);
    response.setDetail(
        hide_file_path(error_output, FileUtils::getFullPath(identifier + "/")));
    send_message_to_client(client_socket, response);
    return 0;
  }

  // 挂载cgroup和命名空间
  std::string cgroup_path = get_cgroup_path(identifier);
  setup_cgroup(cgroup_path, memory_limit);
  setup_mount_namespace(identifier);
  setup_network_namespace();
  setup_pid_namespace();

  std::vector<std::string> input_file_path_list = request->inputFilePathList;
  // 根据inputFilePathList,依次执行用户代码

  std::vector<ExecuteMessage> execute_message_list(
      request->inputFilePathList.size());
  std::vector<JudgeInfo> judge_info_list(request->inputFilePathList.size());
  std::vector<std::string> output_file_path_list(
      request->inputFilePathList.size());
  for (size_t index = 0; index < request->inputFilePathList.size(); index++) {
    std::string input_file_path = request->inputFilePathList[index];
    // 获取文件名部分
    std::string inputFileName =
        std::filesystem::path(input_file_path).filename().string();
    // 分割文件名，获取前缀
    std::string prefix;
    size_t pos = inputFileName.find('_');  // 查找第一个下划线的位置
    if (pos != std::string::npos) {
      prefix = inputFileName.substr(0, pos);  // 提取下划线之前的部分
    }
    std::string user_output_file_path = FileUtils::BASE_PATH;
    user_output_file_path += "/";
    user_output_file_path += FileUtils::QUESTION_SUBMIT_PREFIX;
    user_output_file_path += "/";
    user_output_file_path += identifier;
    user_output_file_path += "/";
    user_output_file_path += prefix;
    user_output_file_path += "_3.ans";

    ExecuteMessage execute_message =
        execute_user_code(user_code_exec.c_str(), input_file_path.c_str(),
                          user_output_file_path.c_str(), time_out);
    execute_message_list[index] = execute_message;
    std::string json_execute_message = nlohmann::json(execute_message).dump(-1);
    log("socketID " + std::to_string(client_socket) +
        " execute_message:" + json_execute_message);
  }

  // 处理execute_message_list
  for (size_t index = 0; index < execute_message_list.size(); index++) {
    ExecuteMessage execute_message = execute_message_list[index];
    JudgeInfo judge_info;
    judge_info.setMemory(execute_message.getMemory());
    judge_info.setTime(std::min(execute_message.getTime(), time_out + 1));

    // 对ExitValue进行switch case，处理judgeInfo，超时判断也在此处信号量中解决
    handle_exit_status(judge_info, execute_message.getExitValue());

    // 进行内存超出判断
    if (judge_info.getMessage().empty()) {
      long execute_memory = judge_info.getMemory();
      if (execute_memory > memory_limit) {
        judge_info.setMessage(JudgeInfoMessage::MEMORY_LIMIT_EXCEEDED);
      }
    }

    judge_info_list[index] = judge_info;

    output_file_path_list[index] = execute_message.getOutputFilePath();
  }
  response.setStatus(QuestionSubmitStatus::SUCCEED);
  response.setJudgeInfoList(judge_info_list);
  response.setOutputFilePathList(output_file_path_list);
  send_message_to_client(client_socket, response);
  // // 关闭客户端连接
  // close(client_socket);

  return 0;  // 正常退出
}

// 处理客户端请求
void handle_client_request(const int client_socket) {
  while (true) {
    char buffer[BUFFER_SIZE] = {0};
    // 接收请求
    int bytes_read = read(client_socket, buffer, sizeof(buffer));
    log("socketID " + std::to_string(client_socket) +
        " Received request: " + buffer);
    if (bytes_read <= 0) {
      close_connection(client_socket, "Failed to read from client.");
      return;
      continue;
    }
    std::string request_str(buffer);
    if (request_str == "ping\n") {
      std::string res = "pong\n";
      send_message_to_client(client_socket, "pong");
      continue;
    }
    SandboxRequest request(request_str);  // 创建 SandboxRequest 实例

    // 分配栈空间
    char* child_stack = (char*)malloc(STACK_SIZE);
    if (!child_stack) {
      close_connection(client_socket,
                       "Failed to allocate memory for child stack.");
      return;
    }
    // 打包参数
    ChildArgs child_args = {&request, client_socket};
    // 创建子进程
    tmp_id = getpid();
    pid_t child_pid = clone(child_fn, child_stack + STACK_SIZE,
                            CLONE_NEWNS | CLONE_NEWPID | SIGCHLD, &child_args);

    if (child_pid == -1) {
      free(child_stack);
      close_connection(client_socket, "Failed to create isolated process.");
      return;
    }
    // 等待子进程完成
    int status;
    waitpid(child_pid, &status, 0);

    if (__WIFEXITED(status)) {
      log("socketID " + std::to_string(client_socket) +
            " Child process exited with status:" + std::to_string(WEXITSTATUS(status)));
    } else if (WIFSIGNALED(status)) {
      log("socketID " + std::to_string(client_socket) +
            " Child process was terminated by signal: " + std::to_string(WTERMSIG(status)),true);
    }

    // 清理资源
    free(child_stack);
  }
}

// 初始化服务器并开始监听
int initialize_server() {
  int server_fd;
  struct sockaddr_in address;
  int opt = 1;

  // 创建 socket 文件描述符
  if ((server_fd = socket(AF_INET, SOCK_STREAM, 0)) == 0) {
    perror("Socket failed");
    exit(EXIT_FAILURE);
  }

  // 设置 socket 选项
  if (setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR | SO_REUSEPORT, &opt,
                 sizeof(opt)) != 0) {
    perror("setsockopt failed");
    exit(EXIT_FAILURE);
  }

  // 配置地址
  address.sin_family = AF_INET;
  address.sin_addr.s_addr = INADDR_ANY;
  address.sin_port = htons(PORT);

  // 绑定 socket
  if (bind(server_fd, (struct sockaddr*)&address, sizeof(address)) < 0) {
    perror("Bind failed");
    exit(EXIT_FAILURE);
  }

  // 开始监听连接
  if (listen(server_fd, 3) < 0) {
    perror("Listen failed");
    exit(EXIT_FAILURE);
  }

  return server_fd;
}

// 接受连接并处理请求
void accept_and_handle_requests(int server_fd) {
  sockaddr_in address;
  int addrlen = sizeof(address);
  int client_socket;

  log("C++ sandbox server is running on port " + PORT);

  while (true) {
    // 接受客户端连接
    if ((client_socket = accept(server_fd, (struct sockaddr*)&address,
                                (socklen_t*)&addrlen)) < 0) {
      perror("Accept failed");
      continue;  // 继续等待下一个连接
    }
    std::thread([client_socket]() {
      try {
        handle_client_request(client_socket);
      } catch (const std::exception& e) {
        log("socketID " + std::to_string(client_socket) +
                " Exception in client handler: " + std::string(e.what()),
            true);
      }
      close(client_socket);
    }).detach();
  }
}

// 使用 epoll 监听多个客户端的事件，并在事件发生时非阻塞处理，支持异步操作
void epoll_server_loop(int server_fd) {
  int epoll_fd = epoll_create1(0);
  if (epoll_fd == -1) {
    perror("epoll_create1 failed");
    exit(EXIT_FAILURE);
  }

  struct epoll_event ev, events[MAX_EVENTS];
  ev.events = EPOLLIN;  // 监听读事件
  ev.data.fd = server_fd;
  if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, server_fd, &ev) == -1) {
    perror("epoll_ctl failed");
    exit(EXIT_FAILURE);
  }
  log("C++ sandbox server is running on port " + PORT);
  while (true) {
    int nfds = epoll_wait(epoll_fd, events, MAX_EVENTS, -1);
    for (int i = 0; i < nfds; ++i) {
      if (events[i].data.fd == server_fd) {
        // 接受新连接
        int client_socket = accept(server_fd, nullptr, nullptr);
        if (client_socket == -1) {
          perror("accept failed");
          continue;
        }
        ev.events = EPOLLIN | EPOLLET;  // 读事件 + 边缘触发
        ev.data.fd = client_socket;
        epoll_ctl(epoll_fd, EPOLL_CTL_ADD, client_socket, &ev);
      } else {
        // 处理客户端请求
        std::thread(handle_client_request, static_cast<int>(events[i].data.fd)).detach();

      }
    }
  }
}

int main()
{
    int server_fd = initialize_server(); // 初始化服务器

    // 接受并处理连接请求
    // accept_and_handle_requests(server_fd);

  epoll_server_loop(server_fd);

    return 0;
}
