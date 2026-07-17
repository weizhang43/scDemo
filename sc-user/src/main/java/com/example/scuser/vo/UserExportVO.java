package com.example.scuser.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import com.alibaba.excel.annotation.write.style.HeadStyle;
import com.alibaba.excel.enums.poi.FillPatternTypeEnum;
import com.alibaba.excel.enums.poi.HorizontalAlignmentEnum;
import lombok.Data;

@Data
@ColumnWidth(18)
@HeadStyle(fillPatternType = FillPatternTypeEnum.SOLID_FOREGROUND, fillForegroundColor = 45)
@ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
public class UserExportVO {

    @ExcelProperty(value = "序号")
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private Integer index;

    @ExcelProperty(value = "用户名")
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private String uName;

    @ExcelProperty(value = "真实姓名")
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private String realName;

    @ExcelProperty(value = "性别")
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private String gender;

    @ExcelProperty(value = "手机号")
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private String phone;

    @ExcelProperty(value = "生日")
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private String birthday;

    public static UserExportVO of(com.curry.model.User u, int index) {
        UserExportVO vo = new UserExportVO();
        vo.setIndex(index);
        vo.setUName(u.getUName());
        vo.setRealName(u.getRealName());
        vo.setGender(genderText(u.getGender()));
        vo.setPhone(u.getPhone());
        vo.setBirthday(u.getBirthday());
        return vo;
    }

    private static String genderText(Integer g) {
        if (g == null) return "未知";
        switch (g) {
            case 0: return "保密";
            case 1: return "男";
            case 2: return "女";
            default: return "未知";
        }
    }
}
