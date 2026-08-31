package com.ruikao.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruikao.common.constant.JwtClaimsConstant;
import com.ruikao.common.properties.JwtProperties;
import com.ruikao.common.utils.JwtUtil;
import com.ruikao.pojo.dto.WebSocketMessageDTO;
import com.ruikao.server.security.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket服务
 * 用于在学生交卷后向前端推送待阅卷通知
 * 握手时校验 JWT（URL 携带 token），仅放行有效登录用户，
 * 且 sid 与 token 身份强绑定：管理端固定 sid=admin，学生端 sid 必须为自身 userId
 */
@Component
@ServerEndpoint("/ws/{sid}")
@Slf4j
public class WebSocketServer {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** 管理端连接标识（前端固定使用） */
    private static final String TYPE_ADMIN = "admin";
    private static final String TYPE_STUDENT = "student";

    /** 消息类型：1-待阅卷提醒 */
    private static final int TYPE_MARK_REMINDER = 1;

    /** 静态注入：端点实例由容器创建，无法使用 @Autowired 字段 */
    private static JwtProperties jwtProperties;
    private static TokenBlacklistService tokenBlacklistService;

    @Autowired
    public void setJwtProperties(JwtProperties jwtProperties) {
        WebSocketServer.jwtProperties = jwtProperties;
    }

    @Autowired
    public void setTokenBlacklistService(TokenBlacklistService tokenBlacklistService) {
        WebSocketServer.tokenBlacklistService = tokenBlacklistService;
    }

    /** 存放会话对象，sid -> 会话信息（含用户类型） */
    private static final Map<String, SessionInfo> sessionMap = new ConcurrentHashMap<>();

    private static class SessionInfo {
        final Session session;
        final String userType;

        SessionInfo(Session session, String userType) {
            this.session = session;
            this.userType = userType;
        }
    }

    /**
     * 连接建立成功调用的方法（握手鉴权：校验 URL 查询参数上的 token + sid 身份绑定）
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        // 从 URL 查询参数取 token（jakarta.websocket 2.0 无 @QueryParam，用 Session API）
        String token = null;
        Map<String, List<String>> params = session.getRequestParameterMap();
        List<String> tokens = params != null ? params.get("token") : null;
        if (tokens != null && !tokens.isEmpty()) {
            token = tokens.get(0);
        }

        ResolvedIdentity identity = resolveIdentity(token);
        if (identity == null) {
            log.warn("WebSocket 连接被拒绝: sid={}, token 无效或已登出", sid);
            closeQuietly(session);
            return;
        }

        // sid 与身份强绑定：管理端固定 sid=admin；学生端 sid 必须等于自身 userId。
        // 防止学生伪造 sid=admin 混入管理端广播通道，或顶掉管理端同名连接
        if (!isSidAllowed(sid, identity)) {
            log.warn("WebSocket 连接被拒绝: sid={} 与 token 身份不匹配, type={}", sid, identity.userType);
            closeQuietly(session);
            return;
        }
        log.info("WebSocket客户端连接: sid={}, type={}, userId={}", sid, identity.userType, identity.userId);
        // 同 sid 重连：先关闭旧连接，防止孤儿连接悬挂占用资源
        SessionInfo old = sessionMap.put(sid, new SessionInfo(session, identity.userType));
        if (old != null && old.session != null && old.session.isOpen()) {
            closeQuietly(old.session);
        }
    }

    /**
     * 收到客户端消息后调用的方法
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        log.info("收到来自客户端({})的消息: {}", sid, message);
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(Session session, @PathParam("sid") String sid) {
        log.info("WebSocket连接断开: sid={}", sid);
        // 条件删除：仅当当前条目仍是本会话时才移除，避免误删同 sid 重连后的新连接
        SessionInfo info = sessionMap.get(sid);
        if (info != null && info.session == session) {
            sessionMap.remove(sid, info);
        }
    }

    /** 解析出的连接身份：用户类型 + 用户ID */
    private static class ResolvedIdentity {
        final String userType;
        final Long userId;

        ResolvedIdentity(String userType, Long userId) {
            this.userType = userType;
            this.userId = userId;
        }
    }

    /**
     * 校验 token：管理端密钥或学生端密钥任一有效即通过，并校验未登出（黑名单）
     *
     * @return 身份信息（用户类型 + userId），无效或已登出返回 null
     */
    private ResolvedIdentity resolveIdentity(String token) {
        if (token == null || token.isEmpty() || jwtProperties == null) {
            return null;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        // 优先管理端密钥
        try {
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
            if (tokenBlacklistService != null && tokenBlacklistService.isBlacklisted("admin", token)) {
                return null;
            }
            return new ResolvedIdentity(TYPE_ADMIN, readUserId(claims));
        } catch (Exception ignored) {
            // 尝试学生端密钥
        }
        try {
            Claims claims = JwtUtil.parseJWT(jwtProperties.getStudentSecretKey(), token);
            if (tokenBlacklistService != null && tokenBlacklistService.isBlacklisted("student", token)) {
                return null;
            }
            return new ResolvedIdentity(TYPE_STUDENT, readUserId(claims));
        } catch (Exception ex) {
            log.warn("WebSocket token 校验失败: {}", ex.getMessage());
            return null;
        }
    }

    private Long readUserId(Claims claims) {
        Object userId = claims.get(JwtClaimsConstant.USER_ID);
        return userId != null ? Long.valueOf(userId.toString()) : null;
    }

    /**
     * sid 与身份绑定校验：管理端必须用 sid=admin，学生端必须用 sid=自身 userId
     */
    private boolean isSidAllowed(String sid, ResolvedIdentity identity) {
        if (TYPE_ADMIN.equals(identity.userType)) {
            return TYPE_ADMIN.equals(sid);
        }
        return identity.userId != null && String.valueOf(identity.userId).equals(sid);
    }

    private void closeQuietly(Session session) {
        try {
            session.close();
        } catch (IOException ignored) {
            // 关闭失败无需处理
        }
    }

    /**
     * 向所有管理端连接广播消息（学生连接不推送，防止信息泄露）
     *
     * @param message 待发送的消息
     */
    private void sendToAllClient(String message) {
        Collection<SessionInfo> sessions = sessionMap.values();
        for (SessionInfo info : sessions) {
            if (!TYPE_ADMIN.equals(info.userType)) {
                continue;
            }
            Session session = info.session;
            try {
                if (session.isOpen()) {
                    session.getBasicRemote().sendText(message);
                }
            } catch (Exception e) {
                log.error("WebSocket发送消息失败, sid={}", session.getId(), e);
            }
        }
    }

    /**
     * 推送待阅卷提醒
     *
     * @param recordId    考试记录ID
     * @param examName    考试名称
     * @param studentName 学生姓名
     */
    public void sendMarkReminder(Long recordId, String examName, String studentName) {
        try {
            WebSocketMessageDTO msg = new WebSocketMessageDTO();
            msg.setType(TYPE_MARK_REMINDER);
            msg.setRecordId(recordId);
            msg.setContent("学生「" + studentName + "」已完成考试《" + examName + "》，请及时批阅");
            msg.setExamName(examName);
            msg.setStudentName(studentName);

            String json = objectMapper.writeValueAsString(msg);
            log.info("WebSocket推送待阅卷提醒: {}", json);
            sendToAllClient(json);
        } catch (Exception e) {
            log.error("WebSocket推送待阅卷提醒失败", e);
        }
    }
}