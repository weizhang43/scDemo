package com.example.scuser.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.User;
import com.example.scuser.mapper.UserMapper;
import com.example.scuser.service.UserService;
import com.example.scuser.vo.UserExportVO;
import org.springframework.stereotype.Service;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final long CODE_TTL_MS = 5 * 60 * 1000L;

    private final Map<String, CodeEntry> codeStore = new ConcurrentHashMap<>();

    @Override
    public ResponseDto<User> register(User user) {
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
        baseMapper.insert(user);
        return ResponseDto.success(null);
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
        CodeEntry(String code, long expireAt) {
            this.code = code;
            this.expireAt = expireAt;
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
