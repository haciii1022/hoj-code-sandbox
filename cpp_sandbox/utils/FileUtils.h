#ifndef FILEUTILS_H
#define FILEUTILS_H

#include <string>

class FileUtils {
public:
    // 常量路径
    static const std::string BASE_PATH;
    static const std::string QUESTION_SUBMIT_PREFIX;
    static const std::string TEMP_DIR;

    // 静态方法
    static std::string getFullPath(const std::string& relativePath);
    static bool saveToFile(const std::string& filePath, const std::string& content);
    static std::string readFromFile(const std::string& filePath);
    static bool ensureDirectoryExists(const std::string& fullFilePath);
};

#endif // FILEUTILS_H
