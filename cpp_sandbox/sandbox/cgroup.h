#ifndef CGROUP_H
#define CGROUP_H

#include <string>

// 设置 cgroup，需传入动态生成的 cgroup 路径
void setup_cgroup(const std::string& cgroup_path, const long memory_limit);

// 清理 cgroup，需传入对应的 cgroup 路径
void cleanup_cgroup(const std::string& cgroup_path);

// 生成唯一的 cgroup 路径
std::string get_cgroup_path(const std::string& identifier);

#endif // CGROUP_H
