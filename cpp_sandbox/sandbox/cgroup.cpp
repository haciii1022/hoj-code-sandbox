#include <dirent.h>
#include <fcntl.h>
#include <cstring>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <uuid/uuid.h>

#include <fstream>
#include <iostream>
#include <string>
static constexpr long long fixed_overhead_kb = 2176;
void enable_controller(const std::string& parent_path,
                       const std::string& controller) {
  std::string subtree_control = parent_path + "/cgroup.subtree_control";
  int fd = open(subtree_control.c_str(), O_WRONLY);
  if (fd < 0) {
    perror(("Failed to open " + subtree_control).c_str());
    return;
  }
  std::string control = "+" + controller + "\n";
  if (write(fd, control.c_str(), control.size()) < 0) {
    perror(("Failed to enable " + controller).c_str());
  }
  close(fd);
}

bool controller_enabled(const std::string& path,
                        const std::string& controller) {
  std::ifstream file(path + "/cgroup.controllers");
  std::string line;
  while (std::getline(file, line)) {
    if (line.find(controller) != std::string::npos) {
      return true;
    }
  }
  return false;
}

// 生成一个 UUID 字符串
std::string generate_uuid() {
  uuid_t uuid;
  uuid_generate(uuid);
  char uuid_str[37];
  uuid_unparse(uuid, uuid_str);
  return std::string(uuid_str);
}

// 创建动态路径
std::string get_cgroup_path(const std::string& identifier) {
  // return std::string("/sys/fs/cgroup/sandbox_") + "333";
  return std::string("/sys/fs/cgroup/sandbox_") + identifier;
  // return std::string("/sys/fs/cgroup/sandbox_") + generate_uuid();
  // return std::string("/home/ubuntu/sandbox_cgroup/") + generate_uuid();
}
void set_cpu_limit(const std::string& cgroup_path, const std::string& limit) {
  std::ofstream cpu_max_file(cgroup_path + "/cpu.max");
  if (!cpu_max_file) {
    std::cerr << "Failed to open cpu.max" << std::endl;
    return;
  }
  cpu_max_file << limit << std::endl;
  cpu_max_file.close();
}

// memory_limit 单位kb
void set_memory_limit(const std::string& cgroup_path, const long memory_limit) {
  std::ofstream memory_limit_file(cgroup_path + "/memory.max");
  if (!memory_limit_file) {
    std::cerr << "Failed to open memory.max" << std::endl;
    return;
  }
  memory_limit_file << memory_limit * 1024 << std::endl;
  memory_limit_file.close();
}

void enable_oom_killer(const std::string& cgroup_path) {
  std::string oom_group_file = cgroup_path + "/memory.oom.group";

  // Open the memory.oom.group file
  std::ofstream oom_group(oom_group_file);
  if (oom_group.is_open()) {
    // Write "1" to enable OOM killer
    oom_group << "1";
    oom_group.close();
    // std::cout << "OOM killer enabled for cgroup: " << cgroup_path << std::endl;
  } else {
    // If unable to open, print error message
    std::cerr << "Failed to enable OOM killer. Unable to open " << oom_group_file
              << ": " << strerror(errno) << std::endl;
  }
}

// 设置 cgroup 限制
void setup_cgroup(const std::string& cgroup_path, const long memory_limit) {
  std::string parent_path = "/sys/fs/cgroup";
  // 创建 cgroup 目录
  if (mkdir(cgroup_path.c_str(), 0755) != 0 && errno != EEXIST) {
    // 判断目录是否已存在
    perror("mkdir");
    return;
  }
  // 确保父路径启用 cpu 控制器
  if (!controller_enabled(parent_path, "cpu")) {
    enable_controller(parent_path, "cpu");
  }
  // // // 检查并启用 uuid 路径的子控制器
  // if (!controller_enabled(cgroup_path, "cpu")) {
  //   enable_controller(cgroup_path, "cpu");
  // }

  if (!controller_enabled(parent_path, "memory")) {
    enable_controller(parent_path, "memory");
  }
  // 限制 CPU 使用
  set_cpu_limit(cgroup_path, "50000 100000");
  // enable_oom_killer(cgroup_path);
  // 限制内存使用
  set_memory_limit(cgroup_path, memory_limit * 3 + fixed_overhead_kb);
  std::string procs_file_path = cgroup_path + "/cgroup.procs";
  // 将当前进程加入 cgroup
  std::ofstream procs(procs_file_path);
  const int pid = getpid();
  std::cout << "This is the child process22. My PID: " << pid << std::endl;
  if (procs.is_open()) {
    procs << std::to_string(pid) << "\n";
    procs.close();
    // std::cout << "Successfully added PID " << pid << " to cgroup." << std::endl;
  } else {
    std::cerr << "Failed to add process to cgroup" << std::endl;
  }
}

void cleanup_cgroup(const std::string& cgroup_path) {
  // 1. 确保没有进程
  std::string procs_file_path = cgroup_path + "/cgroup.procs";
  std::ofstream procs_file(procs_file_path);
  if (procs_file.is_open()) {
    procs_file << "0";  // 将所有进程移到父 cgroup
    procs_file.close();
    // std::cout << "All processes moved to parent cgroup." << std::endl;
  } else {
    std::cerr << "Failed to open " << procs_file_path << ": " << strerror(errno)
              << std::endl;
  }

  // 2. 禁用子控制器
  std::string subtree_control_path = cgroup_path + "/cgroup.subtree_control";
  std::ofstream subtree_control(subtree_control_path);
  if (subtree_control.is_open()) {
    subtree_control << "";  // 清空子控制器
    subtree_control.close();
    // std::cout << "Subtree controllers disabled." << std::endl;
  } else {
    std::cerr << "Failed to open " << subtree_control_path << ": "
              << strerror(errno) << std::endl;
  }

  // 3. 确保目录为空
  DIR* dir = opendir(cgroup_path.c_str());
  if (dir) {
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
      std::string entry_name = entry->d_name;

      // 跳过 "." 和 ".."
      if (entry_name == "." || entry_name == "..") {
        continue;
      }

      std::string entry_path = cgroup_path + "/" + entry_name;

      // 删除文件
      if (unlink(entry_path.c_str()) != 0) {
        std::cerr << "Failed to unlink " << entry_path << ": "
                  << strerror(errno) << std::endl;
      } else {
        // std::cout << "Deleted " << entry_path << std::endl;
      }
    }
    closedir(dir);
  } else {
    std::cerr << "Failed to open directory " << cgroup_path << ": "
              << strerror(errno) << std::endl;
  }

  // 4. 删除 cgroup 目录
    if (rmdir(cgroup_path.c_str()) != 0) {
        std::cerr << "Failed to remove cgroup directory: " << strerror(errno) << std::endl;
    } else {
        // std::cout << "Cgroup cleaned up successfully: " << cgroup_path << std::endl;
    }
}
