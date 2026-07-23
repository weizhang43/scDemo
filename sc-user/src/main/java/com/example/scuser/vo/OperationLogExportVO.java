package com.example.scuser.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import com.alibaba.excel.annotation.write.style.HeadStyle;
import com.alibaba.excel.enums.poi.FillPatternTypeEnum;
import com.alibaba.excel.enums.poi.HorizontalAlignmentEnum;
import com.curry.model.OperationLog;
import lombok.Data;

import java.util.Date;

@Data
@ColumnWidth(18)
@HeadStyle(fillPatternType = FillPatternTypeEnum.SOLID_FOREGROUND, fillForegroundColor = 45)
@ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
public class OperationLogExportVO {

    @ExcelProperty("日志ID")
    private Long logId;

    @ExcelProperty("用户名")
    private String uName;

    @ExcelProperty("模块")
    private String module;

    @ExcelProperty("操作类型")
    private String opType;

    @ExcelProperty("描述")
    private String description;

    @ExcelProperty("请求URI")
    private String requestUri;

    @ExcelProperty("IP")
    private String ip;

    @ExcelProperty("耗时(ms)")
    private Long costMs;

    @ExcelProperty("执行结果")
    private String statusText;

    @ExcelProperty("创建时间")
    private Date createTime;

    public static OperationLogExportVO of(OperationLog log) {
        OperationLogExportVO vo = new OperationLogExportVO();
        vo.setLogId(log.getLogId());
        vo.setUName(log.getUName());
        vo.setModule(log.getModule());
        vo.setOpType(log.getOpType());
        vo.setDescription(log.getDescription());
        vo.setRequestUri(log.getRequestUri());
        vo.setIp(log.getIp());
        vo.setCostMs(log.getCostMs());
        vo.setStatusText(log.getStatus() != null && log.getStatus() == 1 ? "成功" : "失败");
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }
}
