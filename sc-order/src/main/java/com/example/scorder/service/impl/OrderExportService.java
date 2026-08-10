package com.example.scorder.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.curry.model.Order;
import com.example.scorder.auth.OrderScope;
import com.example.scorder.mapper.OrderMapper;
import com.example.scorder.vo.OrderExportVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 订单 Excel 导出：从 OrderServiceImpl 拆出的导出逻辑，
 * 分页查询 + 复用 ExcelWriter 分批写入响应流，避免一次性全量载入内存导致 OOM。
 */
@Component
public class OrderExportService {

    /** 导出分页批大小 */
    private static final int EXPORT_PAGE_SIZE = 1000;

    /** 导出 Sheet 名与文件名 */
    private static final String EXPORT_SHEET_NAME = "订单列表";

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 按查询条件导出订单 Excel（分批写入响应输出流）。
     *
     * @param key             下单人关键字（模糊匹配，可空）
     * @param orderNo         订单号（模糊匹配，可空）
     * @param createTimeStart 创建时间起（可空）
     * @param createTimeEnd   创建时间止（可空）
     * @param scope           可见范围，顾客只能导出自己的订单
     * @param response        Servlet 响应，文件名以 UTF-8 编码避免中文乱码
     * @throws IOException 写响应流失败
     */
    public void export(String key, String orderNo, Date createTimeStart, Date createTimeEnd,
                       OrderScope scope, HttpServletResponse response) throws IOException {
        LambdaQueryWrapper<Order> queryWrapper = buildExportWrapper(
                key, orderNo, createTimeStart, createTimeEnd, scope);
        writeExcelHeader(response);
        try (ExcelWriter writer = EasyExcel.write(response.getOutputStream(), OrderExportVO.class).build()) {
            WriteSheet sheet = EasyExcel.writerSheet(EXPORT_SHEET_NAME).build();
            writeAllPages(writer, sheet, queryWrapper);
        }
    }

    /**
     * 组装导出查询条件：与订单列表查询同口径，按创建时间与主键倒序。
     */
    private LambdaQueryWrapper<Order> buildExportWrapper(String key, String orderNo, Date createTimeStart,
                                                         Date createTimeEnd, OrderScope scope) {
        return new LambdaQueryWrapper<Order>()
                .eq(scope.getOwnerUId() != null, Order::getUId, scope.getOwnerUId())
                .like(key != null && !key.isEmpty(), Order::getAddPerson, key)
                .like(orderNo != null && !orderNo.isEmpty(), Order::getOrderNo, orderNo)
                .ge(createTimeStart != null, Order::getCreateTime, createTimeStart)
                .le(createTimeEnd != null, Order::getCreateTime, createTimeEnd)
                .orderByDesc(Order::getCreateTime)
                .orderByDesc(Order::getOId);
    }

    /**
     * 设置响应头：xlsx 类型 + UTF-8 文件名。
     */
    private void writeExcelHeader(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode(EXPORT_SHEET_NAME, "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
    }

    /**
     * 逐页查询并写入 Sheet，直到取空或不足一整页为止。
     */
    private void writeAllPages(ExcelWriter writer, WriteSheet sheet, LambdaQueryWrapper<Order> queryWrapper) {
        int pageNo = 1;
        int serial = 0;
        boolean hasMore = true;
        while (hasMore) {
            Page<Order> page = new Page<>(pageNo, EXPORT_PAGE_SIZE, false);
            List<Order> records = orderMapper.selectPage(page, queryWrapper).getRecords();
            if (records == null || records.isEmpty()) {
                if (pageNo == 1) {
                    // 空数据也要写一次空列表，保证导出文件带表头
                    writer.write(new ArrayList<OrderExportVO>(), sheet);
                }
                hasMore = false;
            } else {
                List<OrderExportVO> rows = new ArrayList<>(records.size());
                for (Order order : records) {
                    serial++;
                    rows.add(OrderExportVO.of(order, serial));
                }
                writer.write(rows, sheet);
                hasMore = records.size() >= EXPORT_PAGE_SIZE;
                pageNo++;
            }
        }
    }
}
