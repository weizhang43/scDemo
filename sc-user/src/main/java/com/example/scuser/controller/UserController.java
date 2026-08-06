package com.example.scuser.controller;

import com.curry.model.User;
import com.curry.model.auth.AuthConstant;
import com.curry.model.auth.LoginUser;
import com.example.scuser.dto.RegisterRequest;
import com.example.scuser.service.TokenService;
import com.example.scuser.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
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

@RestController
@RequestMapping(value = "/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private TokenService tokenService;

    @PostMapping("/register")
    public ResponseDto<User> register(@RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    public ResponseDto<User> login(@RequestParam("uName") String uName,
                                  @RequestParam("password") String password) {
        ResponseDto<User> result = userService.login(uName, password);
        if (result.getCode() != null && result.getCode() == 200 && result.getDaoResult() != null) {
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

    @PostMapping("/logout")
    public ResponseDto<User> logout(@RequestHeader(value = AuthConstant.HEADER_AUTHORIZATION, required = false) String auth) {
        if (auth == null || !auth.startsWith(AuthConstant.BEARER_PREFIX)) {
            return ResponseDto.error("未登录");
        }
        String token = auth.substring(AuthConstant.BEARER_PREFIX.length()).trim();
        tokenService.revoke(token);
        return ResponseDto.success(null);
    }

    @GetMapping("/me")
    public ResponseDto<User> me(@RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
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

    @PostMapping("/sendSmsCode")
    public ResponseDto<User> sendSmsCode(@RequestParam("phone") String phone) {
        return userService.sendSmsCode(phone);
    }

    @PostMapping("/sendEmailCode")
    public ResponseDto<User> sendEmailCode(@RequestParam("email") String email) {
        return userService.sendEmailCode(email);
    }

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

    @GetMapping("/list")
    public ResponseDto<User> list(@RequestParam(value = "key", required = false, defaultValue = "") String key,
                                @RequestParam(value = "gender", required = false) Integer gender,
                                @RequestParam(value = "birthdayStart", required = false, defaultValue = "") String birthdayStart,
                                @RequestParam(value = "birthdayEnd", required = false, defaultValue = "") String birthdayEnd,
                                @RequestParam("pageNo") int pageNo,
                                @RequestParam("pageSize") int pageSize) {
        return userService.queryUser(key, gender, birthdayStart, birthdayEnd, pageNo, pageSize);
    }

    @GetMapping("/{id}")
    public User get(@PathVariable("id") Integer id) {
        return userService.getById(id);
    }

    @GetMapping("/detail/{id}")
    public ResponseDto<User> getDetail(@PathVariable("id") Integer id) {
        return userService.getDetailById(id);
    }

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
                        @RequestParam(value = "birthdayStart", required = false, defaultValue = "") String birthdayStart,
                        @RequestParam(value = "birthdayEnd", required = false, defaultValue = "") String birthdayEnd,
                        HttpServletResponse response) throws Exception {
        userService.export(key, gender, birthdayStart, birthdayEnd, response);
    }
}
