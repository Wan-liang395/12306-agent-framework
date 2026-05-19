package com.grace.train12306.biz.agentservice.context;

/**
 * Agent 请求上下文，用于在 Controller 和 Tool 之间传递用户信息
 */
public final class AgentRequestContext {

    private static final ThreadLocal<String> USERNAME_HOLDER = new ThreadLocal<>();

    private static final ThreadLocal<String> USERID_HOLDER = new ThreadLocal<>();

    public static void setUsername(String username) {
        USERNAME_HOLDER.set(username);
    }
    public static String getUsername() {
        return USERNAME_HOLDER.get();
    }

    public static void setUserId(String userId) { USERID_HOLDER.set(userId); }
    public static String getUserId() { return USERID_HOLDER.get(); }

    public static void clear() {
        USERNAME_HOLDER.remove();
        USERID_HOLDER.remove();
    }
}
