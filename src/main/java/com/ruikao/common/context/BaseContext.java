package com.ruikao.common.context;
public class BaseContext {
    private static final ThreadLocal<Long> threadLocal = new ThreadLocal<>();
    private static final ThreadLocal<Integer> userTypeThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<String> usernameThreadLocal = new ThreadLocal<>();

    public static void setCurrentId(Long id) { threadLocal.set(id); }
    public static Long getCurrentId() { return threadLocal.get(); }

    /** 当前登录用户类型（0-管理员 1-教师），学生端请求为 null */
    public static void setCurrentUserType(Integer userType) { userTypeThreadLocal.set(userType); }
    public static Integer getCurrentUserType() { return userTypeThreadLocal.get(); }

    /** 当前登录用户用户名，供操作日志切面取操作人 */
    public static void setCurrentUsername(String username) { usernameThreadLocal.set(username); }
    public static String getCurrentUsername() { return usernameThreadLocal.get(); }

    public static void remove() {
        threadLocal.remove();
        userTypeThreadLocal.remove();
        usernameThreadLocal.remove();
    }
}
