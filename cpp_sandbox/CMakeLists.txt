cmake_minimum_required(VERSION 3.30)  # 设置 CMake 最低版本

project(cpp_sandbox)  # 设置项目名称

set(CMAKE_CXX_STANDARD 17)

# 引入 nlohmann/json 库
include(FetchContent)

FetchContent_Declare(
        nlohmann_json
        GIT_REPOSITORY https://github.com/nlohmann/json.git
        GIT_TAG        v3.11.2  # 可以选择具体版本
)

FetchContent_MakeAvailable(nlohmann_json)

# 包含当前目录的头文件
include_directories(.)

# 创建可执行文件
add_executable(cpp_sandbox
        sandbox/cgroup.cpp
        sandbox/compile.cpp
        sandbox/execute.cpp
        sandbox/sandbox.cpp
        utils/FileUtils.cpp
        utils/NamespaceUtils.h
        model/SandboxRequest.cpp
        model/ExecuteCodeResponse.cpp
        model/JudgeInfo.cpp
        model/ExecuteMessage.cpp
        model/ExecuteMessage.h
        common/QuestionSubmitStatus.h
        common/JudgeInfoMessage.h)
# 将 utils 目录添加到 cpp_sandbox 的包含路径中
target_include_directories(cpp_sandbox PRIVATE ${CMAKE_SOURCE_DIR}/utils)
# 将 model 目录添加到 cpp_sandbox 的包含路径中
target_include_directories(cpp_sandbox PRIVATE ${CMAKE_SOURCE_DIR}/model)
# 将 common 目录添加到 cpp_sandbox 的包含路径中
target_include_directories(cpp_sandbox PRIVATE ${CMAKE_SOURCE_DIR}/common)
# 链接 UUID 和 nlohmann_json 库
target_link_libraries(cpp_sandbox PRIVATE uuid nlohmann_json::nlohmann_json)
