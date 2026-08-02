package com.ruikao.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruikao.pojo.dto.WebSocketMessageDTO;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket服务 —— 仿苍穹外卖来单提醒
 * 用于在学生交卷后向前端推送待阅卷通知
 */
@Component
@ServerEndpoint("/ws/{sid}")
@Slf4j
public class WebSocketServer {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** 存放会话对象，sid -> Session */
    private static final Map<String, Session> sessionMap = new HashMap<>();

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        log.info("WebSocket客户端连接: sid={}", sid);
        sessionMap.put(sid, session);
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
    public void onClose(@PathParam("sid") String sid) {
        log.info("WebSocket连接断开: sid={}", sid);
        sessionMap.remove(sid);
    }

    /**
     * 向所有连接的客户端广播消息
     *
     * @param message 待发送的消息
     */
    public void sendToAllClient(String message) {
        Collection<Session> sessions = sessionMap.values();
        for (Session session : sessions) {
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
     * 推送待阅卷提醒（仿苍穹外卖来单提醒）
     *
     * @param recordId    考试记录ID
     * @param examName    考试名称
     * @param studentName 学生姓名
     */
    public void sendMarkReminder(Long recordId, String examName, String studentName) {
        try {
            WebSocketMessageDTO msg = new WebSocketMessageDTO();
            msg.setType(1); // type=1 表示待阅卷提醒
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