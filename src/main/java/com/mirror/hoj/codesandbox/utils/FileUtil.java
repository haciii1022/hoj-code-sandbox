package com.mirror.hoj.codesandbox.utils;


import cn.hutool.extra.spring.SpringUtil;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.mirror.hoj.codesandbox.client.SftpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

;
;

/**
 * 文件工具类
 *
 * @author Mirror.
 * @date 2024/11/27
 */
@Slf4j
public final class FileUtil {

    private FileUtil() {
    }

    private static ChannelSftp channelSftp;

    private static final SftpClient sftpClient;

    private static final int PERMISSIONS_755 = 0755;

    public static final String ROOT_PATH = "/home/ubuntu";

    public static final String QUESTION_SUBMIT_PREFIX = "hoj/questionSubmit";

    static {
        sftpClient = SpringUtil.getBean(SftpClient.class);
    }

    /**
     * 通过 SFTP 上传文件到远程服务器
     *
     * @param originalFile 需要上传的文件
     * @param fullFilePath 远程服务器上文件保存的完整路径
     */
    public static void saveFileViaSFTP(MultipartFile originalFile, String fullFilePath) {
        channelSftp = sftpClient.getChannelSftp();
        // 获取文件所在的目录
        String folderPath = Paths.get(fullFilePath).getParent().toString();
        log.info("folderPath:{}", folderPath);
        ensureDirectoryExists(folderPath);
        try (InputStream inputStream = originalFile.getInputStream()) {
            // 上传文件到远程服务器的指定路径
            log.info("保存文件：{}", fullFilePath);
            channelSftp.put(inputStream, fullFilePath);
            setFilePermissions(fullFilePath, PERMISSIONS_755);
        } catch (Exception e) {
            log.error("保存文件失败,上传路径：{}", fullFilePath, e);
        }
    }

    public static void saveFileViaSFTP(File originalFile, String fullFilePath) {
        channelSftp = sftpClient.getChannelSftp();
        // 获取文件所在的目录
        String folderPath = Paths.get(fullFilePath).getParent().toString();
        log.info("folderPath:{}", folderPath);
        ensureDirectoryExists(folderPath);
        try (InputStream inputStream = Files.newInputStream(originalFile.toPath())) {
            // 上传文件到远程服务器的指定路径
            log.info("保存文件：{}", fullFilePath);
            channelSftp.put(inputStream, fullFilePath);
            setFilePermissions(fullFilePath, PERMISSIONS_755); // 设置文件权限为 755
        } catch (Exception e) {
            log.error("保存文件失败,上传路径：{}", fullFilePath, e);
        }
    }

    /**
     * 删除远程服务器上的文件
     *
     * @param remoteFilePath 需要删除的远程文件的完整路径
     * @throws SftpException 如果删除文件失败或文件不存在
     */
    public static void deleteFileViaSFTP(String remoteFilePath) throws SftpException {
        channelSftp = sftpClient.getChannelSftp();
        try {
            // 检查文件是否存在
            SftpATTRS attrs = channelSftp.stat(remoteFilePath);
            if (attrs != null) {
                channelSftp.rm(remoteFilePath); // 删除文件
                log.info("文件已删除：{}", remoteFilePath);
            }
        } catch (SftpException e) {
            // 如果文件不存在或无法访问，则抛出异常
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                log.info("文件不存在：{}", remoteFilePath);
            } else {
                throw e;  // 重新抛出其他异常
            }
        }
    }

    /**
     * 通过 SFTP 下载文件并返回 ResponseEntity<byte[]> 以供浏览器下载
     *
     * @param remoteFilePath 远程文件的完整路径
     * @return ResponseEntity<byte [ ]> 包含文件的字节流
     */
    public static Resource downloadFileViaSFTP(String remoteFilePath) {
        channelSftp = sftpClient.getChannelSftp();
        // 从远程路径获取文件流
        InputStream inputStream = null;
        log.info("下载文件：{}", remoteFilePath);
        try {
            inputStream = channelSftp.get(remoteFilePath);
        } catch (SftpException e) {
            log.error("文件下载失败，远程文件路径：{}", remoteFilePath, e);
            throw new RuntimeException("执行错误: " + e.getMessage(), e);
        }

        // 使用 InputStreamResource 将文件流包装成 Resource 对象
        return new InputStreamResource(inputStream);
    }

    /**
     * 确保远程服务器的目录存在
     * 如果目录不存在，则逐级创建
     */
    private static void ensureDirectoryExists(String remoteDir) {
        channelSftp = sftpClient.getChannelSftp();
        String[] folders = remoteDir.split("/");
        StringBuilder pathBuilder = new StringBuilder("/");

        for (String folder : folders) {
            if (folder.isEmpty()) continue;  // 忽略空字符串（路径开始部分的斜杠）
            pathBuilder.append(folder).append("/");
            String currentPath = pathBuilder.toString();

            if (!isDirectoryExist(currentPath)) {
                try {
                    channelSftp.mkdir(currentPath); // 创建目录
                    setFilePermissions(currentPath, PERMISSIONS_755);
                } catch (SftpException e) {
                    log.error("文件路径创建失败：{}", currentPath, e);
//                    throw new RuntimeException("文件路径创建失败：" + currentPath, e);
                }

            }
        }
    }

    /**
     * 判断远程目录是否存在
     */
    public static boolean isDirectoryExist(String remoteDir) {
        channelSftp = sftpClient.getChannelSftp();
        try {
            SftpATTRS attrs = channelSftp.stat(remoteDir);
            return attrs.isDir();  // 如果是目录，返回 true
        } catch (SftpException e) {
            return false;  // 如果发生异常，目录不存在
        }
    }

    /**
     * 比对两个文件的内容是否一致，忽略最后一行的换行符（\n 或 \r\n）
     *
     * @param filePath1 第一个文件的完整路径
     * @param filePath2 第二个文件的完整路径
     * @return true 如果文件内容一致（忽略最后一行的换行符），否则 false
     */
    public static boolean compareFilesIgnoringLastLineEnding(String filePath1, String filePath2) {
        channelSftp = sftpClient.getChannelSftp();
        try (InputStream inputStream1 = channelSftp.get(filePath1);
             InputStream inputStream2 = channelSftp.get(filePath2)) {

            // 将输入流读取为去掉最后一行换行符后的字节数组
            byte[] file1Bytes = readStreamIgnoringLastLineEnding(inputStream1);
            byte[] file2Bytes = readStreamIgnoringLastLineEnding(inputStream2);

            // 比较两个文件的字节数组是否相等
            return DigestUtils.md5DigestAsHex(file1Bytes).equals(DigestUtils.md5DigestAsHex(file2Bytes));

        } catch (Exception e) {
            log.error("文件比对失败，文件路径：{} 和 {}", filePath1, filePath2, e);
            return false;
        }
    }

    /**
     * 读取输入流，并忽略最后一行的换行符（\n 或 \r\n）
     *
     * @param inputStream 文件输入流
     * @return 去掉最后一行换行符的字节数组
     */
    private static byte[] readStreamIgnoringLastLineEnding(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        // 转换为字符串并去除末尾的换行符
        String fileContent = outputStream.toString("UTF-8").replaceFirst("(\\r?\\n)+$", "");

        // 将处理后的字符串转换回字节数组
        return fileContent.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 将 Resource 转换为 File 并保存
     *
     * @param resource   Spring Resource 对象
     * @param targetFile 目标文件
     * @throws IOException 如果文件写入过程中发生错误
     */
    public static void resourceToFile(Resource resource, File targetFile) {
        try (InputStream inputStream = resource.getInputStream()) {
            // 确保目标文件的父目录存在
            Path targetPath = targetFile.toPath();
            log.info("targetPath: {}", targetPath);
            Files.createDirectories(targetPath.getParent());

            // 使用 Files.copy 方法将 InputStream 内容写入目标文件
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            setFilePermissions(targetFile.getAbsolutePath(), PERMISSIONS_755);
            log.info("文件已保存: " + targetFile.getAbsolutePath());

        } catch (IOException | SftpException e) {
            log.error("文件转换失败，文件路径：{}", targetFile.getAbsolutePath(), e);
            throw new RuntimeException("文件转换失败", e);
        }
    }

    /**
     * 设置远程文件或目录的权限
     *
     * @param remotePath  远程文件或目录的路径
     * @param permissions 文件权限（例如：0755）
     */
    private static void setFilePermissions(String remotePath, int permissions) throws SftpException {
        String chmodCommand = "chmod " + String.format("%04o", permissions) + " " + remotePath;
        channelSftp.chmod(permissions, remotePath);
        log.info("设置权限 {} 给文件 {}", String.format("%04o", permissions), remotePath);
    }
}
