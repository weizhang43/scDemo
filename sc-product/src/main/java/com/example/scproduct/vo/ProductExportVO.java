package com.example.scproduct.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import com.alibaba.excel.annotation.write.style.HeadStyle;
import com.alibaba.excel.enums.poi.FillPatternTypeEnum;
import com.alibaba.excel.enums.poi.HorizontalAlignmentEnum;
import lombok.Data;

import java.util.Date;

@Data
@ColumnWidth(18)
@HeadStyle(fillPatternType = FillPatternTypeEnum.SOLID_FOREGROUND, fillForegroundColor = 45)
@ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
public class ProductExportVO {

    @ExcelProperty(value = "序号")
    private Integer index;

    @ExcelProperty(value = "商品名称")
    private String pName;

    @ExcelProperty(value = "生产日期")
    private Date productionDate;

    @ExcelProperty(value = "保质期(天)")
    private Integer shelfLife;

    @ExcelProperty(value = "产地")
    private String origin;

    @ExcelProperty(value = "厂家名称")
    private String manufacturer;

    @ExcelProperty(value = "库存")
    private Integer stock;

    @ExcelProperty(value = "价格")
    private Integer price;

    @ExcelProperty(value = "状态")
    private String status;

    public static ProductExportVO of(com.curry.model.Product p, int index) {
        ProductExportVO vo = new ProductExportVO();
        vo.setIndex(index);
        vo.setPName(p.getPName());
        vo.setProductionDate(p.getProductionDate());
        vo.setShelfLife(p.getShelfLife());
        vo.setOrigin(p.getOrigin());
        vo.setManufacturer(p.getManufacturer());
        vo.setStock(p.getStock());
        vo.setPrice(p.getPrice());
        vo.setStatus(p.getIsExpired() != null && p.getIsExpired() == 1 ? "已过期" : "正常");
        return vo;
    }
}
