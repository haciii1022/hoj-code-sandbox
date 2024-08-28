package com.mirror.hoj.codesandbox.security;

/**
 * @author Mirror
 * @date 2024/8/27
 */
public class DenySecurityManager extends SecurityManager{
    @Override
    public void checkPermission(java.security.Permission perm) {
        // 拒绝所有操作
        System.out.println("DenySecurityManager.checkPermission");
        throw new SecurityException("Access denied");
    }
    @Override
    public void checkRead(String file) {
        throw new SecurityException("checkRead 权限异常：" + file);
    }
    @Override
    public void checkWrite(String file) {
        throw new SecurityException("checkWrite 权限异常：" + file);
    }
    @Override
    public void checkExec(String cmd) {
        throw new SecurityException("checkExec 权限异常：" + cmd);
    }
    @Override
    public void checkConnect(String host, int port) {
        throw new SecurityException("checkConnect 权限异常：" + host + ":" + port);
    }

}
