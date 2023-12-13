package com.tastsong.crazycar.controller;

import com.tastsong.crazycar.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tastsong.crazycar.common.Result;
import com.tastsong.crazycar.common.ResultCode;
import com.tastsong.crazycar.service.LoginService;
import com.tastsong.crazycar.utils.Util;

import cn.hutool.json.JSONObject;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@Scope("prototype")
@RequestMapping(value = "/v2/UserInfo")
public class UserInfoController {
    @Autowired
    private LoginService loginService;
    @Autowired
    private UserService userService;

    @PostMapping(value = "/GetUser")
    public Object getUserInfo(@RequestBody JSONObject body) throws Exception {
        int uid = body.getInt("uid");
        if(userService.isExistsUserByUid(uid)){
            String userName = userService.getUserByUid(uid).getUser_name();
            return loginService.getUserInfo(userName);
        } else{
            return Result.failure(ResultCode.RC404);
        }
    }

    @PostMapping(value = "/ModifyPassword")
    public Object ModifyPassword(@RequestBody JSONObject body, @RequestHeader(Util.TOKEN) String token) throws Exception{
        int uid = Util.getUidByToken(token);
        String password = body.getStr("password");
        if(password.length() >= 6){
            userService.changePassword(uid, password);
            return Result.success();
        } else {
            return Result.failure(ResultCode.RC423);
        }
    }
}
