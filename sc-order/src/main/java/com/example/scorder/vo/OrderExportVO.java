package com.example.scorder.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import com.alibaba.excel.annotation.write.style.HeadStyle;
import com.alibaba.excel.enums.poi.FillPatternTypeEnum;
import com.alibaba.excel.enums.poi.HorizontalAlignmentEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@ColumnWidth(18)
@HeadStyle(fillPatternType = FillPatternTypeEnum.SOLID_FOREGROUND, fillForegroundColor = 45)
@ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
public class OrderExportVO {

    @ExcelProperty(value = "序号")
    private Integer index;

    @ExcelProperty(value = "订单编号")
    private String orderNo;

    @ExcelProperty(value = "下单人")
    private String addPerson;

    @ExcelProperty(value = "下单时间")
    private Date createTime;

    @ExcelProperty(value = "下单地址")
    private String orderAddress;

    @ExcelProperty(value = "订单金额")
    private BigDecimal orderAmount;

    @ExcelProperty(value = "订单状态")
    private String orderStatus;

    /**
     * 工厂方法：将订单实体转换为导出 VO，并填入序号与状态文案。
     */
    public static OrderExportVO of(com.curry.model.Order o, int index) {
        OrderExportVO vo = new OrderExportVO();
        vo.setIndex(index);
        vo.setOrderNo(o.getOrderNo());
        vo.setAddPerson(o.getAddPerson());
        vo.setCreateTime(o.getCreateTime());
        vo.setOrderAddress(o.getOrderAddress());
        vo.setOrderAmount(o.getOrderAmount());
        vo.setOrderStatus(statusText(o.getOrderStatus()));
        return vo;
    }

    /**
     * 将订单状态码转换为导出文案：0 取消 / 1 已下单 / 2 已完成，其它为"未知"。
     */
    private static String statusText(Integer s) {
        if (s == null) return "未知";
        switch (s) {
            case 0: return "订单取消";
            case 1: return "已下单";
            case 2: return "已完成";
            default: return "未知";
        }
    }
}
