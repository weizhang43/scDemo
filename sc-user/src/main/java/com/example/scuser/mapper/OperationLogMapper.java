package com.example.scuser.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.curry.model.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {

    /** 分页查询操作日志，联 t_user 现取操作人真实姓名；操作人条件同时匹配登录名与真实姓名 */
    @Select("<script>"
            + "SELECT l.*, u.real_name AS realName"
            + " FROM t_operation_log l"
            + " LEFT JOIN t_user u ON u.u_name = l.u_name"
            + " <where>"
            + " <if test='uName != null and uName != \"\"'>"
            + " AND (l.u_name LIKE CONCAT('%', #{uName}, '%')"
            + " OR u.real_name LIKE CONCAT('%', #{uName}, '%'))"
            + " </if>"
            + " <if test='module != null and module != \"\"'> AND l.module = #{module} </if>"
            + " <if test='opType != null and opType != \"\"'> AND l.op_type = #{opType} </if>"
            + " <if test='status != null'> AND l.status = #{status} </if>"
            + " <if test='beginTime != null and beginTime != \"\"'> AND l.create_time &gt;= #{beginTime} </if>"
            + " <if test='endTime != null and endTime != \"\"'> AND l.create_time &lt;= #{endTime} </if>"
            + " </where>"
            + " ORDER BY l.log_id DESC"
            + "</script>")
    IPage<OperationLog> selectPageWithRealName(Page<OperationLog> page,
                                               @Param("uName") String uName,
                                               @Param("module") String module,
                                               @Param("opType") String opType,
                                               @Param("status") Integer status,
                                               @Param("beginTime") String beginTime,
                                               @Param("endTime") String endTime);
}
