#ifndef NAMESPACE_UTILS_H
#define NAMESPACE_UTILS_H

#include <sys/mount.h>
#include <sys/stat.h>  // 提供 mkdir 函数的声明
#include <unistd.h>

#include <cerrno>  // 提供 errno 宏的定义
#include <iostream>

#include "FileUtils.h"

// 挂载 namespace 的设置
void setup_mount_namespace(const std::string& identifier) {
  // 定义沙箱根路径
  const std::string base_path = FileUtils::BASE_PATH + "/cpp_sandbox";
  const std::string sandbox_path = base_path + "/sandbox_" + identifier;
  // 确保沙箱根目录存在
  if (mkdir(base_path.c_str(), 0755) == -1 && errno != EEXIST) {
    std::cerr << "Failed to create base path " << base_path << ": "
              << strerror(errno) << std::endl;
    exit(EXIT_FAILURE);
  }

  // 创建沙箱目录
  if (mkdir(sandbox_path.c_str(), 0755) == -1 && errno != EEXIST) {
    std::cerr << "Failed to create sandbox directory " << sandbox_path << ": "
              << strerror(errno) << std::endl;
    exit(EXIT_FAILURE);
  }

  // 挂载根目录为只读
  mount(nullptr, "/", nullptr, MS_REC | MS_PRIVATE, nullptr);

  // 绑定挂载沙箱目录
  if (mount(sandbox_path.c_str(), sandbox_path.c_str(), nullptr,
            MS_BIND | MS_REC, nullptr) == -1) {
    std::cerr << "Failed to bind mount sandbox directory: " << strerror(errno)
              << std::endl;
    exit(EXIT_FAILURE);
  }

  // // 绑定系统目录为只读
  // const std::vector<std::string> system_dirs = {"/usr", "/lib", "/lib64", "/bin"};
  // for (const auto& dir : system_dirs) {
  //   std::string target_path = sandbox_path + dir;
  //   if (mkdir(target_path.c_str(), 0755) == -1 && errno != EEXIST) {
  //     std::cerr << "Failed to create directory " << target_path << ": " << strerror(errno) << std::endl;
  //     exit(EXIT_FAILURE);
  //   }
  //   if (mount(dir.c_str(), target_path.c_str(), nullptr, MS_BIND | MS_REC, nullptr) == -1) {
  //     std::cerr << "Failed to bind mount " << dir << ": " << strerror(errno) << std::endl;
  //     exit(EXIT_FAILURE);
  //   }
  //   // 尝试重新挂载为只读
  //   if (mount(nullptr, target_path.c_str(), nullptr, MS_REMOUNT | MS_RDONLY, nullptr) == -1) {
  //     std::cerr << "Failed to remount " << dir << " as read-only: " << strerror(errno) << std::endl;
  //     // 如果失败，只输出警告而不退出
  //   }
  // }

  // 设置沙箱路径为工作目录
  if (chdir(sandbox_path.c_str()) != 0) {
    perror("Failed to change working directory");
    exit(EXIT_FAILURE);
  }

}

// 网络 namespace 的设置
void setup_network_namespace() {
  system("ip link set lo up");  // 启用 loopback 网络
}

// PID namespace 的设置
void setup_pid_namespace() {
    // std::cout << "PID namespace set up." << std::endl;
}

#endif
