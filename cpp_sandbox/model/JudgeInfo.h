#ifndef JUDGE_INFO_H
#define JUDGE_INFO_H

#include <string>
#include <nlohmann/json.hpp>
class JudgeInfo {
public:
    // 构造函数
    JudgeInfo() = default;
    JudgeInfo(const std::string& message, const std::string& detail, long time, long memory);

    // Getter 和 Setter
    const std::string& getMessage() const;
    void setMessage(const std::string& message);

    const std::string& getDetail() const;
    void setDetail(const std::string& message);

    long getTime() const;
    void setTime(long time);

    long getMemory() const;
    void setMemory(long memory);
    // 支持 JSON 序列化
    NLOHMANN_DEFINE_TYPE_INTRUSIVE(JudgeInfo,
        message,
        detail,
        time,
        memory)
private:
    std::string message; // 程序执行信息
    std::string detail;  // 程序执行详情
    long time;           // 消耗时间 (ms)
    long memory;         // 消耗内存 (KB)
};

#endif // JUDGE_INFO_H
