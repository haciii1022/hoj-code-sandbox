public class DefaultSecurityManager extends SecurityManager{
    @Override
    public void checkPermission(java.security.Permission perm) {
        // 默认不做任何处理，允许所有操作
        System.out.println("默认不做处理: "+perm.toString());
//        super.checkPermission(perm);
    }
}