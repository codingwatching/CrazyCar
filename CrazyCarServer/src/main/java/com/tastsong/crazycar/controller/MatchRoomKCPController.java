package com.tastsong.crazycar.controller;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServlet;

import cn.hutool.json.JSONUtil;
import com.tastsong.crazycar.dto.req.ReqRoomMsg;
import com.tastsong.crazycar.model.UserModel;
import com.tastsong.crazycar.service.*;
import org.springframework.context.ApplicationContext;

import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backblaze.erasure.fec.Snmp;
import com.tastsong.crazycar.config.ApplicationContextRegister;
import com.tastsong.crazycar.model.MatchClassModel;
import com.tastsong.crazycar.dto.resp.RespMatchRoomPlayer;
import com.tastsong.crazycar.utils.Util;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import kcp.ChannelConfig;
import kcp.KcpListener;
import kcp.KcpServer;
import kcp.Ukcp;
import lombok.extern.slf4j.Slf4j;

@RestController
@Scope("prototype")
@Slf4j
@RequestMapping(value = "/v2/KCP")
public class MatchRoomKCPController extends HttpServlet implements KcpListener {
    private boolean isInit = false;
    private static final ConcurrentHashMap<String, Ukcp> kcpSet = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ArrayList<RespMatchRoomPlayer>> roomMap = new ConcurrentHashMap<String, ArrayList<RespMatchRoomPlayer>>();
    private static int onlineCount = 0;
    private MatchClassService matchClassService;
    private UserService userService;
    
    private ArrayList<RespMatchRoomPlayer> playerLists = new ArrayList<RespMatchRoomPlayer>();

    public MatchRoomKCPController() {
        super();
    }

    @PostMapping(value = "/MatchRoom")
    public Object doGet() throws Exception {
        JSONObject data = new JSONObject();
        if (!isInit) {
            initKCP();
            isInit = true;
        }
        data.putOpt("KCP", "KCP");
        return data;
    }

    private void initKCP() {
        MatchRoomKCPController kcpRttServer = new MatchRoomKCPController();

        ChannelConfig channelConfig = new ChannelConfig();
        channelConfig.nodelay(true, 10, 2, true);
        channelConfig.setSndwnd(300);
        channelConfig.setRcvwnd(300);
        channelConfig.setMtu(512);
        channelConfig.setAckNoDelay(true);
        channelConfig.setTimeoutMillis(10000);
        channelConfig.setCrc32Check(false);
        KcpServer kcpServer = new KcpServer();
        kcpServer.init(kcpRttServer, channelConfig, 50002);
    }

    @Override
    public void onConnected(Ukcp uKcp) {
        onlineCount++;
        ApplicationContext act = ApplicationContextRegister.getApplicationContext();
        matchClassService = act.getBean(MatchClassService.class);
        userService = act.getBean(UserService.class);
        log.info("Connected onlineCount = " + onlineCount);
    }

    @Override
    public void handleReceive(ByteBuf buf, Ukcp kcp) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), bytes);
        ReqRoomMsg req = JSONUtil.toBean(buf.toString(CharsetUtil.UTF_8), ReqRoomMsg.class);
        int msgType = req.getMsg_type();
        if (msgType == Util.msgType.MatchRoomCreate) {
            onCreateRoom(req, kcp);
        } else if (msgType == Util.msgType.MatchRoomJoin) {
            onJoinRoom(req, kcp);
        } else if (msgType == Util.msgType.MatchRoomExit) {
            onExitRoom(req);
        } else if (msgType == Util.msgType.MatchRoomStart) {
            onStartRoom(req);
        } else if (msgType == Util.msgType.MatchRoomStatus) {
            onStatusRoom(req);
        }
    }

    private void onCreateRoom(ReqRoomMsg req, Ukcp kcp) {
        int uid = req.getUid();
        String roomId = req.getRoom_id();
        String id = uid + "," + roomId;
        kcpSet.put(id, kcp);
        String token = req.getToken();
        JSONObject data = new JSONObject();
        data.putOpt("msg_type", Util.msgType.MatchRoomCreate);
        data.putOpt("uid", uid);
        if (!Util.isLegalToken(token)) {
            data.putOpt("code", 423);
        } else if (MatchRoomKCPController.roomMap.containsKey(roomId)) {
            data.putOpt("code", 421);
        } else {
            UserModel userModel = userService.getUserByUid(uid);
            RespMatchRoomPlayer info = matchClassService.toRespMatchRoom(uid, userModel.getEid(), true);
            ArrayList<RespMatchRoomPlayer> list = new ArrayList<>();
            list.add(info);
            MatchRoomKCPController.roomMap.put(roomId, list);
            data.putOpt("code", 200);
        }
        log.info("OnCreateRoom : " + data.toString());
        sendToUser(data, roomId);
    }

    private void onJoinRoom(ReqRoomMsg req, Ukcp kcp) {
        int uid = req.getUid();
        String roomId = req.getRoom_id();
        String id = uid + "," + roomId;
        kcpSet.put(id, kcp);
        String token = req.getToken();
        JSONObject data = new JSONObject();
        data.putOpt("msg_type", Util.msgType.MatchRoomJoin);
        int maxNum = 2;
        if (!Util.isLegalToken(token)) {
            data.putOpt("code", 422);
        } else if (!MatchRoomKCPController.roomMap.containsKey(roomId)) {
            data.putOpt("code", 404);
        } else if (MatchRoomKCPController.roomMap.get(roomId).size() >= maxNum) {
            data.putOpt("code", 423);
        } else {
            UserModel userModel = userService.getUserByUid(uid);
            RespMatchRoomPlayer info = matchClassService.toRespMatchRoom(uid, userModel.getEid(), false);
            MatchRoomKCPController.roomMap.get(roomId).add(info);
            data.putOpt("code", 200);
        }
        log.info("OnCreateRoom : " + data.toString());
        sendToUser(data, roomId);
    }

    private void onStatusRoom(ReqRoomMsg req) {
        String roomId = req.getRoom_id();
        JSONObject data = new JSONObject();
        data.putOpt("msg_type", Util.msgType.MatchRoomStatus);
        if (!MatchRoomKCPController.roomMap.containsKey(roomId)) {
            data.putOpt("code", 404);
        } else {
            JSONArray jsonArray = new JSONArray();
            playerLists = MatchRoomKCPController.roomMap.get(roomId);
            jsonArray.addAll(playerLists);
            data.putOpt("players", jsonArray);
            data.putOpt("code", 200);
        }
        log.info("OnStatusRoom : " + data.toString());
        sendToUser(data, roomId);
    }

    private void onExitRoom(ReqRoomMsg req) {
        int uid = req.getUid();
        String roomId = req.getRoom_id();
        String id = uid + "," + roomId;
        JSONObject data = new JSONObject();
        data.putOpt("msg_type", Util.msgType.MatchRoomExit);
        if (!MatchRoomKCPController.roomMap.containsKey(roomId)) {
            data.putOpt("code", 404);
        } else {
            data.putOpt("exit_uid", uid);
            JSONArray jsonArray = new JSONArray();
            playerLists = MatchRoomKCPController.roomMap.get(roomId);
            // 不能在此处删除此Player在roomMap的数据，因为一会还需要发送给此玩家发消息
            for (RespMatchRoomPlayer playerList : playerLists) {
                if (uid != playerList.getUid()) {
                    jsonArray.add(playerList);
                }
            }
            data.putOpt("players", jsonArray);
            data.putOpt("code", 200);
        }
        log.info("onExitRoom : " + data.toString());
        sendToUser(data, roomId);
        exitRoom(id);
    }

    private void onStartRoom(ReqRoomMsg req) {
        String roomId = req.getRoom_id();
        int mapCid = req.getCid();
        MatchClassModel infoModel = matchClassService.createOneMatch(mapCid, roomId);
        JSONObject data = new JSONObject();
        data.putOpt("msg_type", Util.msgType.MatchRoomStart);
        if (!MatchRoomKCPController.roomMap.containsKey(roomId)) {
            data.putOpt("code", 404);
        } else {
            data.putOpt("cid", infoModel.getCid());
            data.putOpt("name", infoModel.getClass_name());
            data.putOpt("star", infoModel.getStar());
            data.putOpt("map_id", infoModel.getMap_id());
            data.putOpt("limit_time", infoModel.getLimit_time());
            data.putOpt("times", infoModel.getTimes());
            data.putOpt("start_time", infoModel.getStart_time());
            data.putOpt("enroll_time", infoModel.getEnroll_time());
            data.putOpt("code", 200);
        }
        log.info("onStartRoom : " + data.toString());
        sendToUser(data, roomId);
    }

    private void sendToUser(JSONObject message, String roomId) {
        for (String key : kcpSet.keySet()) {
            if (key.split(",")[1].equals(roomId)) {
                byte[] bytes = message.toString().getBytes(CharsetUtil.UTF_8);
                ByteBuf buf = Unpooled.wrappedBuffer(bytes);
                kcpSet.get(key).write(buf);
            }
        }
    }

    @Override
    public void handleException(Throwable ex, Ukcp kcp) {
        log.info(ex.getMessage());
    }

    @Override
    public void handleClose(Ukcp uKcp) {
        log.info("handleClose " + Snmp.snmp.toString());
        Snmp.snmp = new Snmp();
        for (String key : kcpSet.keySet()) {
            if(kcpSet.get(key) == uKcp){
                exitRoom(key);
            }
        }
        log.info("onClose");
    }

    private void exitRoom(String id){
        int curUid = Integer.parseInt(id.split(",")[0]);
        String roomId = id.split(",")[1];
        if (MatchRoomKCPController.roomMap.containsKey(roomId)) {
            for (int i = 0; i < MatchRoomKCPController.roomMap.get(roomId).size(); i++) {
                if (MatchRoomKCPController.roomMap.get(roomId).get(i).getUid() == curUid) {
                    MatchRoomKCPController.roomMap.get(roomId).remove(i);
                    if (MatchRoomKCPController.roomMap.get(roomId).isEmpty()) {
                        MatchRoomKCPController.roomMap.remove(roomId);
                    }
                    break;
                }
            }
            log.info("exitRoom id = : " + id);
        }
        kcpSet.remove(id);
        onlineCount--; // 在线数减1
        log.info("onclose sum = " + onlineCount);
    }
}
