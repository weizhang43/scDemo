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

    /** 性别：保密 */
    private static final int GENDER_SECRET = 0;

    /** 性别：男 */
    private static final int GENDER_MALE = 1;

    /** 性别：女 */
    private static final int GENDER_FEMALE = 2;

    /**
     * 由用户实体构建导出行。
     * @param u 用户实体
     * @param index 序号（从 1 开始）
     */
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

    /**
     * 性别编码转中文文案，未知编码显示“未知”。
     */
    private static String genderText(Integer g) {
        String text;
        if (g == null) {
            text = "未知";
        } else {
            switch (g) {
                case GENDER_SECRET: text = "保密"; break;
                case GENDER_MALE: text = "男"; break;
                case GENDER_FEMALE: text = "女"; break;
                default: text = "未知"; break;
            }
        }
        return text;
    }
}
