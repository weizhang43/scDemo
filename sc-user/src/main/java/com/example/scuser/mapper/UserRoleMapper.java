package com.example.scuser.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.curry.model.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

    /** 查询用户经由"用户-角色-权限"链路拥有的按钮权限标识集合（仅启用的角色与权限） */
    @Select("SELECT DISTINCT m.permission FROM t_user_role ur"
            + " JOIN t_role r ON r.id = ur.role_id AND r.status = 1"
            + " JOIN t_role_module rm ON rm.role_id = ur.role_id"
            + " JOIN t_module m ON m.id = rm.module_id AND m.status = 1"
            + " WHERE ur.user_id = #{userId} AND m.type = 'BTN' AND m.permission IS NOT NULL")
    List<String> selectBtnPermsByUserId(@Param("userId") Integer userId);
}
