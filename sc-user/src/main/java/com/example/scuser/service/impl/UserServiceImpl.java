package com.example.scuser.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.User;
import com.curry.model.auth.AuthConstant;
import com.example.scuser.dto.RegisterRequest;
import com.example.scuser.mapper.UserMapper;
import com.example.scuser.service.UserService;
import com.example.scuser.util.MailUtil;
import com.example.scuser.vo.UserExportVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 用户核心服务：注册、登录、验证码、资料维护、统计与导出。
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);

    private static final long CODE_TTL_MS = 5 * 60 * 1000L;

    /** 邮箱验证码有效期 3 分钟，与短信的 5 分钟不同，故单独定义 */
    private static final long EMAIL_CODE_TTL_MS = 3 * 60 * 1000L;

    /** 同一邮箱的最短重发间隔，与前端 60s 倒计时保持一致 */
    private static final long RESEND_INTERVAL_MS = 60 * 1000L;

    /** 6 位数字验证码的取值上界（不含） */
    private static final int CODE_BOUND = 1000000;

    /** 验证码使用安全随机数，避免被预测（S2245） */
    private static final SecureRandom RANDOM = new SecureRandom();

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
        String error = validateRegisterUser(user);
        if (error != null) {
            return ResponseDto.error(error);
        }
        return doRegister(user, request.getEmailCode());
    }

    /**
     * 注册参数校验，通过返回 null，否则返回错误提示。
     */
    private String validateRegisterUser(User user) {
        String error = validateRegisterBasic(user);
        if (error == null) {
            error = validateRegisterEmailFormat(user);
        }
        if (error == null) {
            error = validateRegisterUnique(user);
        }
        return error;
    }

    /**
     * 基础字段校验：用户名、密码、用户类型。
     */
    private String validateRegisterBasic(User user) {
        String error = null;
        // uType 来自请求体，/user/register 又在网关白名单内；
        // 此处只放行商家/顾客，否则任何人都能注册成管理员
        Integer uType = user.getUType();
        boolean typeAllowed = uType != null
                && (uType == AuthConstant.U_TYPE_MERCHANT || uType == AuthConstant.U_TYPE_CUSTOMER);
        if (user.getUName() == null || user.getUName().trim().isEmpty()) {
            error = "用户名不能为空";
        } else if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            error = "密码不能为空";
        } else if (!typeAllowed) {
            error = "用户类型只能为商家或顾客";
        }
        return error;
    }

    /**
     * 邮箱非空与格式校验。
     */
    private String validateRegisterEmailFormat(User user) {
        String email = user.getEmail() == null ? null : user.getEmail().trim();
        if (email == null || email.isEmpty()) {
            return "邮箱不能为空";
        }
        return EMAIL_PATTERN.matcher(email).matches() ? null : "邮箱格式不正确";
    }

    /**
     * 用户名、手机号、邮箱唯一性校验。
     */
    private String validateRegisterUnique(User user) {
        LambdaQueryWrapper<User> checkWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUName, user.getUName());
        if (!baseMapper.selectList(checkWrapper).isEmpty()) {
            return "用户名已存在";
        }
        boolean hasPhone = user.getPhone() != null && !user.getPhone().trim().isEmpty();
        LambdaQueryWrapper<User> phoneWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getPhone, user.getPhone());
        if (hasPhone && !baseMapper.selectList(phoneWrapper).isEmpty()) {
            return "手机号已被注册";
        }
        LambdaQueryWrapper<User> emailWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getEmail, user.getEmail().trim());
        return baseMapper.selectList(emailWrapper).isEmpty() ? null : "邮箱已被注册";
    }

    /**
     * 校验邮箱验证码后落库。
     */
    private ResponseDto<User> doRegister(User user, String emailCode) {
        String email = user.getEmail().trim();
        ResponseDto<User> codeCheck = verifyEmailCode(email, emailCode);
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
        String error = validateSendEmailCode(addr);
        if (error != null) {
            return ResponseDto.error(error);
        }
        String code = String.format("%06d", RANDOM.nextInt(CODE_BOUND));
        try {
            mailUtil.sendTo(addr, "注册验证码",
                    "您的注册验证码是：" + code + "，3 分钟内有效。若非本人操作请忽略此邮件。");
        } catch (Exception e) {
            // 发送失败就不要写入 code store，否则用户收不到却以为已发送
            LOGGER.warn("[UserService] 注册验证码邮件发送失败 email={}", addr, e);
            return ResponseDto.error("验证码发送失败：" + e.getMessage());
        }
        emailCodeStore.put(addr, new CodeEntry(code, System.currentTimeMillis() + EMAIL_CODE_TTL_MS));
        Map<String, Object> data = new HashMap<>();
        data.put("email", addr);
        return ResponseDto.success(data);
    }

    /**
     * 发送邮箱验证码前的校验：格式、是否已注册、限频。
     */
    private String validateSendEmailCode(String addr) {
        String error = null;
        if (addr == null || addr.isEmpty()) {
            error = "请输入邮箱";
        } else if (!EMAIL_PATTERN.matcher(addr).matches()) {
            error = "邮箱格式不正确";
        } else if (!baseMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getEmail, addr)).isEmpty()) {
            error = "邮箱已被注册";
        } else {
            // 前端 60s 倒计时只是 UI 限制，这个端点在网关白名单内可被匿名直连，
            // 服务端也要限频，否则能拿它对任意地址刷垃圾邮件
            CodeEntry last = emailCodeStore.get(addr);
            if (last != null && System.currentTimeMillis() - last.issuedAt < RESEND_INTERVAL_MS) {
                error = "验证码发送过于频繁，请稍后再试";
            }
        }
        return error;
    }

    /**
     * 校验邮箱验证码，通过返回 null，否则返回带错误信息的响应。
     */
    private ResponseDto<User> verifyEmailCode(String email, String code) {
        String error;
        if (code == null || code.trim().isEmpty()) {
            error = "请输入邮箱验证码";
        } else {
            error = checkCodeEntry(emailCodeStore, email, code.trim());
        }
        return error == null ? null : ResponseDto.error(error);
    }

    /**
     * 校验验证码存储中的条目，通过返回 null，否则返回错误提示；过期时顺带清理。
     */
    private String checkCodeEntry(Map<String, CodeEntry> store, String key, String code) {
        CodeEntry entry = store.get(key);
        if (entry == null) {
            return "验证码未发送，请先获取验证码";
        }
        if (System.currentTimeMillis() > entry.expireAt) {
            store.remove(key);
            return "验证码已过期，请重新获取";
        }
        return entry.code.equals(code) ? null : "验证码错误";
    }

    @Override
    public ResponseDto<User> login(String uName, String password, Integer expectedUType) {
        // TODO 密码当前为明文存储/明文比对，应迁移为加盐哈希（如 BCrypt）存储后再改造比对逻辑；
        //  为不破坏存量数据，此处仅改为先按用户名查询、在应用层比对密码，避免把明文密码拼进查询条件
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUName, uName);
        List<User> userList = baseMapper.selectList(queryWrapper);
        User user = userList.isEmpty() ? null : userList.get(0);
        if (user == null || user.getPassword() == null || !user.getPassword().equals(password)) {
            return ResponseDto.error("用户名或密码错误");
        }
        if (expectedUType != null && !expectedUType.equals(user.getUType())) {
            return ResponseDto.error("该账号不是" + uTypeName(expectedUType) + "账号，请从"
                    + uTypeName(user.getUType()) + "入口登录");
        }
        String token = "token-" + user.getUId() + "-" + System.currentTimeMillis();
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        // 返回不含密码的用户信息（含 realName）
        user.setPassword(null);
        data.put("user", user);
        return ResponseDto.success(data);
    }

    private String uTypeName(Integer uType) {
        if (uType == null) {
            return "未知";
        }
        switch (uType) {
            case AuthConstant.U_TYPE_MERCHANT:
                return "商家";
            case AuthConstant.U_TYPE_CUSTOMER:
                return "顾客";
            case AuthConstant.U_TYPE_ADMIN:
                return "管理员";
            default:
                return "未知";
        }
    }

    @Override
    public ResponseDto<User> addByAdmin(User user) {
        if (user == null) {
            return ResponseDto.error("请求参数不能为空");
        }
        String error = validateAdminAddUser(user);
        if (error != null) {
            return ResponseDto.error(error);
        }
        if (user.getEmail() != null) {
            user.setEmail(user.getEmail().trim());
        }
        user.setUId(null);
        baseMapper.insert(user);
        user.setPassword(null);
        return ResponseDto.success(user);
    }

    /**
     * 管理端新增账号校验：必填项、类型合法、用户名/手机号/邮箱唯一。
     */
    private String validateAdminAddUser(User user) {
        Integer uType = user.getUType();
        boolean typeAllowed = uType != null
                && (uType == AuthConstant.U_TYPE_MERCHANT || uType == AuthConstant.U_TYPE_CUSTOMER
                || uType == AuthConstant.U_TYPE_ADMIN);
        if (user.getUName() == null || user.getUName().trim().isEmpty()) {
            return "用户名不能为空";
        }
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            return "密码不能为空";
        }
        if (!typeAllowed) {
            return "用户类型不合法";
        }
        if (!baseMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getUName, user.getUName().trim())).isEmpty()) {
            return "用户名已存在";
        }
        if (user.getPhone() != null && !user.getPhone().trim().isEmpty()
                && !baseMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, user.getPhone().trim())).isEmpty()) {
            return "手机号已被注册";
        }
        String email = user.getEmail() == null ? "" : user.getEmail().trim();
        if (!email.isEmpty()) {
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                return "邮箱格式不正确";
            }
            if (!baseMapper.selectList(new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, email)).isEmpty()) {
                return "邮箱已被注册";
            }
        }
        return null;
    }

    @Override
    public ResponseDto<User> deleteByAdmin(Integer uId) {
        if (uId == null) {
            return ResponseDto.error("用户ID不能为空");
        }
        User exists = baseMapper.selectById(uId);
        if (exists == null) {
            return ResponseDto.error("用户不存在或已删除");
        }
        // u_name/phone 有唯一索引，逻辑删除前改名/置空，释放给后续注册使用
        String suffix = "_del_" + uId;
        String uName = exists.getUName() == null ? "" : exists.getUName();
        int maxBaseLen = 64 - suffix.length();
        if (uName.length() > maxBaseLen) {
            uName = uName.substring(0, maxBaseLen);
        }
        baseMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getUId, uId)
                .set(User::getUName, uName + suffix)
                .set(User::getPhone, null));
        baseMapper.deleteById(uId);
        return ResponseDto.success(null);
    }

    @Override
    public ResponseDto<User> queryUser(String key, Integer gender, String birthdayStart,
                                       String birthdayEnd, Integer uType, int pageNo, int pageSize) {
        Page<User> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .select(User::getUId, User::getUName, User::getRealName,
                        User::getGender, User::getPhone, User::getBirthday,
                        User::getEmail, User::getUType)
                .eq(gender != null, User::getGender, gender)
                .eq(uType != null, User::getUType, uType)
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
        String code = String.format("%06d", RANDOM.nextInt(CODE_BOUND));
        codeStore.put(phone, new CodeEntry(code, System.currentTimeMillis() + CODE_TTL_MS));
        LOGGER.info("[SMS-MOCK] 向 {} 发送验证码：{}（5 分钟内有效）", phone, code);
        Map<String, Object> data = new HashMap<>();
        data.put("phone", phone);
        return ResponseDto.success(data);
    }

    @Override
    public ResponseDto<User> resetPassword(String phone, String code, String newPassword) {
        String error = validateResetPassword(phone, code, newPassword);
        if (error != null) {
            return ResponseDto.error(error);
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

    /**
     * 重置密码入参与短信验证码校验，通过返回 null，否则返回错误提示。
     */
    private String validateResetPassword(String phone, String code, String newPassword) {
        String error;
        if (phone == null || phone.trim().isEmpty()) {
            error = "请输入手机号";
        } else if (code == null || code.trim().isEmpty()) {
            error = "请输入验证码";
        } else if (newPassword == null || newPassword.trim().isEmpty()) {
            error = "请输入新密码";
        } else {
            error = checkCodeEntry(codeStore, phone, code);
        }
        return error;
    }

    /**
     * 验证码条目：验证码内容、过期时间与签发时间。
     */
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
        String error = exists == null ? "用户不存在" : checkPhoneUsable(user, exists);
        if (error != null) {
            return ResponseDto.error(error);
        }
        return doUpdateProfile(user);
    }

    /**
     * 手机号唯一性校验：仅当手机号有变化时检查是否被他人占用。
     */
    private String checkPhoneUsable(User user, User exists) {
        boolean phoneChanged = user.getPhone() != null && !user.getPhone().trim().isEmpty()
                && !user.getPhone().equals(exists.getPhone());
        if (!phoneChanged) {
            return null;
        }
        LambdaQueryWrapper<User> phoneWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getPhone, user.getPhone())
                .ne(User::getUId, user.getUId());
        return baseMapper.selectList(phoneWrapper).isEmpty() ? null : "手机号已被其他用户使用";
    }

    /**
     * 执行资料更新并返回最新用户信息（不含密码）。
     */
    private ResponseDto<User> doUpdateProfile(User user) {
        // 仅允许修改基本信息，不在此修改用户名与密码
        User update = new User();
        update.setUId(user.getUId());
        update.setRealName(user.getRealName());
        update.setGender(user.getGender());
        update.setPhone(user.getPhone());
        update.setBirthday(user.getBirthday());
        // 传空串表示移除头像；传 null 表示本次不动它（MyBatis-Plus updateById 忽略 null）
        update.setAvatar(user.getAvatar());
        int rows = baseMapper.updateById(update);
        if (rows <= 0) {
            return ResponseDto.error("更新失败");
        }
        User latest = baseMapper.selectById(user.getUId());
        latest.setPassword(null);
        return ResponseDto.success(latest);
    }

    @Override
    public ResponseDto<User> statisticsOverview() {
        long totalUsers = 0L;
        long customerCount = 0L;
        long merchantCount = 0L;
        long adminCount = 0L;
        for (Map<String, Object> row : baseMapper.countGroupByType()) {
            Object type = row.get("uType");
            long cnt = row.get("cnt") == null ? 0L : ((Number) row.get("cnt")).longValue();
            totalUsers += cnt;
            if (type == null) {
                continue;
            }
            switch (((Number) type).intValue()) {
                case AuthConstant.U_TYPE_MERCHANT: merchantCount = cnt; break;
                case AuthConstant.U_TYPE_CUSTOMER: customerCount = cnt; break;
                case AuthConstant.U_TYPE_ADMIN: adminCount = cnt; break;
                default: break;
            }
        }
        Long todayNew = baseMapper.countTodayNew();
        Map<String, Object> data = new HashMap<>();
        data.put("totalUsers", totalUsers);
        data.put("customerCount", customerCount);
        data.put("merchantCount", merchantCount);
        data.put("adminCount", adminCount);
        data.put("todayNewUsers", todayNew == null ? 0L : todayNew);
        return ResponseDto.success(data);
    }

    @Override
    public void export(String key, Integer gender, String birthdayStart, String birthdayEnd,
                       Integer uType, HttpServletResponse response) throws Exception {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .select(User::getUId, User::getUName, User::getRealName,
                        User::getGender, User::getPhone, User::getBirthday, User::getEmail)
                .eq(gender != null, User::getGender, gender)
                .eq(uType != null, User::getUType, uType)
                .ge(birthdayStart != null && !birthdayStart.isEmpty(), User::getBirthday, birthdayStart)
                .le(birthdayEnd != null && !birthdayEnd.isEmpty(), User::getBirthday, birthdayEnd)
                .orderByDesc(User::getUId);
        if (key != null && !key.isEmpty()) {
            queryWrapper.and(w -> w.like(User::getUName, key)
                    .or().like(User::getRealName, key)
                    .or().like(User::getPhone, key));
        }
        List<User> list = baseMapper.selectList(queryWrapper);
        List<UserExportVO> rows = new ArrayList<>();
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
