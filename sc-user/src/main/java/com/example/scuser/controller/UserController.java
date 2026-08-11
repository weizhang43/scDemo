package com.example.scuser.controller;

import com.curry.model.User;
import com.curry.model.annotation.OpLog;
import com.curry.model.auth.AuthConstant;
import com.curry.model.auth.LoginUser;
import com.example.scuser.dto.RegisterRequest;
import com.example.scuser.service.TokenService;
import com.example.scuser.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户接口：注册、登录登出、验证码、资料维护、列表查询与导出。
 */
@RestController
@RequestMapping(value = "/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private TokenService tokenService;

    /**
     * 用户注册（商家/顾客），需要邮箱验证码。
     */
    @PostMapping("/register")
    public ResponseDto<User> register(@RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    /**
     * 用户登录：校验用户名密码后签发 token 并写入 Redis 会话。
     * uType 为登录入口选择的角色，传入时会校验账号类型是否一致。
     */
    @PostMapping("/login")
    public ResponseDto<User> login(@RequestParam("uName") String uName,
                                  @RequestParam("password") String password,
                                  @RequestParam(value = "uType", required = false) Integer uType) {
        ResponseDto<User> result = userService.login(uName, password, uType);
        boolean success = ResponseDto.SUCCESS_CODE.equals(result.getCode())
                && result.getDaoResult() != null;
        if (success) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getDaoResult();
            User user = (User) data.get("user");
            String token = tokenService.issue(LoginUser.of(user.getUId(), user.getUName(),
                    user.getRealName(), user.getUType()));
            data.put("token", token);
            data.put("tokenType", "Bearer");
        }
        return result;
    }

    /**
     * 登出：注销当前 token 对应的会话。
     */
    @PostMapping("/logout")
    public ResponseDto<User> logout(
            @RequestHeader(value = AuthConstant.HEADER_AUTHORIZATION, required = false) String auth) {
        if (auth == null || !auth.startsWith(AuthConstant.BEARER_PREFIX)) {
            return ResponseDto.error("未登录");
        }
        String token = auth.substring(AuthConstant.BEARER_PREFIX.length()).trim();
        tokenService.revoke(token);
        return ResponseDto.success(null);
    }

    /**
     * 当前登录用户信息（由网关透传的请求头还原）。
     */
    @GetMapping("/me")
    public ResponseDto<User> me(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_NAME, required = false) String uName,
            @RequestHeader(value = AuthConstant.HEADER_X_REAL_NAME, required = false) String realName,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("uId", uId);
        data.put("uName", uName);
        data.put("realName", realName);
        data.put("uType", uType);
        return ResponseDto.success(data);
    }

    /**
     * 发送重置密码用的短信验证码。
     */
    @PostMapping("/sendSmsCode")
    public ResponseDto<User> sendSmsCode(@RequestParam("phone") String phone) {
        return userService.sendSmsCode(phone);
    }

    /**
     * 发送注册用的邮箱验证码。
     */
    @PostMapping("/sendEmailCode")
    public ResponseDto<User> sendEmailCode(@RequestParam("email") String email) {
        return userService.sendEmailCode(email);
    }

    /**
     * 凭短信验证码重置密码。
     */
    @PostMapping("/resetPassword")
    public ResponseDto<User> resetPassword(@RequestParam("phone") String phone,
                                           @RequestParam("code") String code,
                                           @RequestParam("newPassword") String newPassword) {
        return userService.resetPassword(phone, code, newPassword);
    }

    /** 管理员驾驶舱：用户总量、按类型构成、今日新增注册数 */
    @GetMapping("/statistics/overview")
    public ResponseDto<User> statisticsOverview(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (uType == null || uType != AuthConstant.U_TYPE_ADMIN) {
            return ResponseDto.error("无权限");
        }
        return userService.statisticsOverview();
    }

    /**
     * 管理员直接新增账号（任意用户类型，免验证码）。
     */
    @OpLog(module = "用户管理", type = OpLog.OpType.ADD, description = "新增用户")
    @PostMapping("/add")
    public ResponseDto<User> add(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer opType,
            @RequestBody User user) {
        if (opType == null || opType != AuthConstant.U_TYPE_ADMIN) {
            return ResponseDto.error("无权限");
        }
        return userService.addByAdmin(user);
    }

    /**
     * 管理员逻辑删除用户，不允许删除自己。
     */
    @OpLog(module = "用户管理", type = OpLog.OpType.DELETE, description = "删除用户")
    @DeleteMapping("/{id}")
    public ResponseDto<User> delete(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer opType,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer opUId,
            @PathVariable("id") Integer id) {
        if (opType == null || opType != AuthConstant.U_TYPE_ADMIN) {
            return ResponseDto.error("无权限");
        }
        if (opUId != null && opUId.equals(id)) {
            return ResponseDto.error("不能删除当前登录账号");
        }
        return userService.deleteByAdmin(id);
    }

    /**
     * 按关键字、性别、用户类型与生日区间分页查询用户列表。
     */
    @GetMapping("/list")
    public ResponseDto<User> list(
            @RequestParam(value = "key", required = false, defaultValue = "") String key,
            @RequestParam(value = "gender", required = false) Integer gender,
            @RequestParam(value = "birthdayStart", required = false, defaultValue = "")
            String birthdayStart,
            @RequestParam(value = "birthdayEnd", required = false, defaultValue = "")
            String birthdayEnd,
            @RequestParam(value = "uType", required = false) Integer uType,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("pageSize") int pageSize) {
        return userService.queryUser(key, gender, birthdayStart, birthdayEnd, uType, pageNo, pageSize);
    }

    /**
     * 按 ID 查询用户实体。
     */
    @GetMapping("/{id}")
    public User get(@PathVariable("id") Integer id) {
        return userService.getById(id);
    }

    /**
     * 按 ID 查询用户详情（不含密码）。
     */
    @GetMapping("/detail/{id}")
    public ResponseDto<User> getDetail(@PathVariable("id") Integer id) {
        return userService.getDetailById(id);
    }

    /**
     * 修改用户基本资料（不含用户名与密码）。
     */
    @PutMapping("/profile")
    public ResponseDto<User> updateProfile(@RequestBody User user) {
        return userService.updateProfile(user);
    }

    /**
     * 按查询条件导出用户列表（EasyExcel）
     */
    @GetMapping("/export")
    public void export(@RequestParam(value = "key", required = false, defaultValue = "") String key,
                        @RequestParam(value = "gender", required = false) Integer gender,
                        @RequestParam(value = "birthdayStart", required = false, defaultValue = "")
                        String birthdayStart,
                        @RequestParam(value = "birthdayEnd", required = false, defaultValue = "")
                        String birthdayEnd,
                        @RequestParam(value = "uType", required = false) Integer uType,
                        HttpServletResponse response) throws Exception {
        userService.export(key, gender, birthdayStart, birthdayEnd, uType, response);
    }
}
