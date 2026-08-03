package com.example.scuser.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.User;
import com.example.scuser.dto.RegisterRequest;
import com.example.scuser.mapper.UserMapper;
import com.example.scuser.service.UserService;
import com.example.scuser.util.MailUtil;
import com.example.scuser.vo.UserExportVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final long CODE_TTL_MS = 5 * 60 * 1000L;

    /** 邮箱验证码有效期 3 分钟，与短信的 5 分钟不同，故单独定义 */
    private static final long EMAIL_CODE_TTL_MS = 3 * 60 * 1000L;

    /** 同一邮箱的最短重发间隔，与前端 60s 倒计时保持一致 */
    private static final long RESEND_INTERVAL_MS = 60 * 1000L;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final Map<String, CodeEntry> codeStore = new ConcurrentHashMap<>();

    /** 邮箱验证码与短信验证码分开存放，避免两套流程互相顶掉 */
    private final Map<String, CodeEntry> emailCodeStore = new ConcurrentHashMap<>();

    @Autowired
    private MailUtil mailUtil;

    @Override
    public ResponseDto<User> register(RegisterRequest request) {
        if (request == null) {
            return ResponseDto.error("请求参数不能为空");
        }
        User user = request.toUser();
        if (user.getUName() == null || user.getUName().trim().isEmpty()) {
            return ResponseDto.error("用户名不能为空");
        }
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            return ResponseDto.error("密码不能为空");
        }
        LambdaQueryWrapper<User> checkWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUName, user.getUName());
        if (!baseMapper.selectList(checkWrapper).isEmpty()) {
            return ResponseDto.error("用户名已存在");
        }
        if (user.getPhone() != null && !user.getPhone().trim().isEmpty()) {
            LambdaQueryWrapper<User> phoneWrapper = new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, user.getPhone());
            if (!baseMapper.selectList(phoneWrapper).isEmpty()) {
                return ResponseDto.error("手机号已被注册");
            }
        }
        // uType 来自请求体，/user/register 又在网关白名单内；
        // 此处只放行商家/顾客，否则任何人都能注册成管理员
        Integer uType = user.getUType();
        if (uType == null || (uType != 1 && uType != 2)) {
            return ResponseDto.error("用户类型只能为商家或顾客");
        }
        String email = user.getEmail() == null ? null : user.getEmail().trim();
        if (email == null || email.isEmpty()) {
            return ResponseDto.error("邮箱不能为空");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return ResponseDto.error("邮箱格式不正确");
        }
        LambdaQueryWrapper<User> emailWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email);
        if (!baseMapper.selectList(emailWrapper).isEmpty()) {
            return ResponseDto.error("邮箱已被注册");
        }
        ResponseDto<User> codeCheck = verifyEmailCode(email, request.getEmailCode());
        if (codeCheck != null) {
            return codeCheck;
        }
        user.setEmail(email);
        baseMapper.insert(user);
        emailCodeStore.remove(email);
        return ResponseDto.success(null);
    }

    @Override
    public ResponseDto<User> sendEmailCode(String email) {
        String addr = email == null ? null : email.trim();
        if (addr == null || addr.isEmpty()) {
            return ResponseDto.error("请输入邮箱");
        }
        if (!EMAIL_PATTERN.matcher(addr).matches()) {
            return ResponseDto.error("邮箱格式不正确");
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>().eq(User::getEmail, addr);
        if (!baseMapper.selectList(wrapper).isEmpty()) {
            return ResponseDto.error("邮箱已被注册");
        }
        // 前端 60s 倒计时只是 UI 限制，这个端点在网关白名单内可被匿名直连，
        // 服务端也要限频，否则能拿它对任意地址刷垃圾邮件
        CodeEntry last = emailCodeStore.get(addr);
        if (last != null && System.currentTimeMillis() - last.issuedAt < RESEND_INTERVAL_MS) {
            return ResponseDto.error("验证码发送过于频繁，请稍后再试");
        }
        String code = String.format("%06d", new Random().nextInt(1000000));
        try {
            mailUtil.sendTo(addr, "注册验证码",
                    "您的注册验证码是：" + code + "，3 分钟内有效。若非本人操作请忽略此邮件。");
        } catch (Exception e) {
            // 发送失败就不要写入 code store，否则用户收不到却以为已发送
            return ResponseDto.error("验证码发送失败：" + e.getMessage());
        }
        emailCodeStore.put(addr, new CodeEntry(code, System.currentTimeMillis() + EMAIL_CODE_TTL_MS));
        Map<String, Object> data = new HashMap<>();
        data.put("email", addr);
        return ResponseDto.success(data);
    }

    /**
     * 校验邮箱验证码，通过返回 null，否则返回带错误信息的响应。
     */
    private ResponseDto<User> verifyEmailCode(String email, String code) {
        if (code == null || code.trim().isEmpty()) {
            return ResponseDto.error("请输入邮箱验证码");
        }
        CodeEntry entry = emailCodeStore.get(email);
        if (entry == null) {
            return ResponseDto.error("验证码未发送，请先获取验证码");
        }
        if (System.currentTimeMillis() > entry.expireAt) {
            emailCodeStore.remove(email);
            return ResponseDto.error("验证码已过期，请重新获取");
        }
        if (!entry.code.equals(code.trim())) {
            return ResponseDto.error("验证码错误");
        }
        return null;
    }

    @Override
    public ResponseDto<User> login(String uName, String password) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUName, uName)
                .eq(User::getPassword, password);
        List<User> userList = baseMapper.selectList(queryWrapper);
        if (userList.isEmpty()) {
            return ResponseDto.error("用户名或密码错误");
        }
        User user = userList.get(0);
        String token = "token-" + user.getUId() + "-" + System.currentTimeMillis();
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        // 返回不含密码的用户信息（含 realName）
        user.setPassword(null);
        data.put("user", user);
        return ResponseDto.success(data);
    }

    @Override
    public ResponseDto<User> queryUser(String key, Integer gender, String birthdayStart, String birthdayEnd, int pageNo, int pageSize) {
        Page<User> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .select(User::getUId, User::getUName, User::getRealName,
                        User::getGender, User::getPhone, User::getBirthday, User::getEmail)
                .eq(gender != null, User::getGender, gender)
                .ge(birthdayStart != null && !birthdayStart.isEmpty(), User::getBirthday, birthdayStart)
                .le(birthdayEnd != null && !birthdayEnd.isEmpty(), User::getBirthday, birthdayEnd)
                .and(w -> w.like(User::getUName, key)
                        .or().like(User::getRealName, key)
                        .or().like(User::getPhone, key))
                .orderByDesc(User::getUId);
        page = baseMapper.selectPage(page, queryWrapper);
        return ResponseDto.success(page);
    }

    @Override
    public ResponseDto<User> sendSmsCode(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return ResponseDto.error("请输入手机号");
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>().eq(User::getPhone, phone);
        List<User> users = baseMapper.selectList(wrapper);
        if (users.isEmpty()) {
            return ResponseDto.error("该手机号未注册");
        }
        String code = String.format("%06d", new Random().nextInt(1000000));
        codeStore.put(phone, new CodeEntry(code, System.currentTimeMillis() + CODE_TTL_MS));
        System.out.println("[SMS-MOCK] 向 " + phone + " 发送验证码：" + code + "（5 分钟内有效）");
        Map<String, Object> data = new HashMap<>();
        data.put("phone", phone);
        return ResponseDto.success(data);
    }

    @Override
    public ResponseDto<User> resetPassword(String phone, String code, String newPassword) {
        if (phone == null || phone.trim().isEmpty()) {
            return ResponseDto.error("请输入手机号");
        }
        if (code == null || code.trim().isEmpty()) {
            return ResponseDto.error("请输入验证码");
        }
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return ResponseDto.error("请输入新密码");
        }
        CodeEntry entry = codeStore.get(phone);
        if (entry == null) {
            return ResponseDto.error("验证码未发送，请先获取验证码");
        }
        if (System.currentTimeMillis() > entry.expireAt) {
            codeStore.remove(phone);
            return ResponseDto.error("验证码已过期，请重新获取");
        }
        if (!entry.code.equals(code)) {
            return ResponseDto.error("验证码错误");
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>().eq(User::getPhone, phone);
        List<User> users = baseMapper.selectList(wrapper);
        if (users.isEmpty()) {
            return ResponseDto.error("该手机号未注册");
        }
        User user = users.get(0);
        user.setPassword(newPassword);
        baseMapper.updateById(user);
        codeStore.remove(phone);
        return ResponseDto.success(null);
    }

    private static class CodeEntry {
        final String code;
        final long expireAt;
        final long issuedAt;
        CodeEntry(String code, long expireAt) {
            this.code = code;
            this.expireAt = expireAt;
            this.issuedAt = System.currentTimeMillis();
        }
    }

    @Override
    public ResponseDto<User> getDetailById(Integer uId) {
        if (uId == null) {
            return ResponseDto.error("用户ID不能为空");
        }
        User user = baseMapper.selectById(uId);
        if (user == null) {
            return ResponseDto.error("用户不存在");
        }
        user.setPassword(null);
        return ResponseDto.success(user);
    }

    @Override
    public ResponseDto<User> updateProfile(User user) {
        if (user == null || user.getUId() == null) {
            return ResponseDto.error("用户ID不能为空");
        }
        User exists = baseMapper.selectById(user.getUId());
        if (exists == null) {
            return ResponseDto.error("用户不存在");
        }
        // 仅允许修改基本信息，不在此修改用户名与密码
        User update = new User();
        update.setUId(user.getUId());
        update.setRealName(user.getRealName());
        update.setGender(user.getGender());
        update.setPhone(user.getPhone());
        update.setBirthday(user.getBirthday());
        // 传空串表示移除头像；传 null 表示本次不动它（MyBatis-Plus updateById 忽略 null）
        update.setAvatar(user.getAvatar());
        // 手机号唯一性校验
        if (user.getPhone() != null && !user.getPhone().trim().isEmpty()
                && !user.getPhone().equals(exists.getPhone())) {
            LambdaQueryWrapper<User> phoneWrapper = new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, user.getPhone())
                    .ne(User::getUId, user.getUId());
            if (!baseMapper.selectList(phoneWrapper).isEmpty()) {
                return ResponseDto.error("手机号已被其他用户使用");
            }
        }
        int rows = baseMapper.updateById(update);
        if (rows <= 0) {
            return ResponseDto.error("更新失败");
        }
        User latest = baseMapper.selectById(user.getUId());
        latest.setPassword(null);
        return ResponseDto.success(latest);
    }

    @Override
    public void export(String key, Integer gender, String birthdayStart, String birthdayEnd, HttpServletResponse response) throws Exception {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .select(User::getUId, User::getUName, User::getRealName,
                        User::getGender, User::getPhone, User::getBirthday, User::getEmail)
                .eq(gender != null, User::getGender, gender)
                .ge(birthdayStart != null && !birthdayStart.isEmpty(), User::getBirthday, birthdayStart)
                .le(birthdayEnd != null && !birthdayEnd.isEmpty(), User::getBirthday, birthdayEnd)
                .orderByDesc(User::getUId);
        if (key != null && !key.isEmpty()) {
            queryWrapper.and(w -> w.like(User::getUName, key)
                    .or().like(User::getRealName, key)
                    .or().like(User::getPhone, key));
        }
        List<User> list = baseMapper.selectList(queryWrapper);
        List<UserExportVO> rows = new java.util.ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            rows.add(UserExportVO.of(list.get(i), i + 1));
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("用户列表", "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), UserExportVO.class)
                .sheet("用户列表")
                .doWrite(rows);
    }
}
