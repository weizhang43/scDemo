package com.example.scproduct.vo;

import lombok.Data;

@Data
public class ExportTaskVO {
    private String taskId;
    private String status;
    private Long total;
    private Long processed;
    private Integer progress;
    private String fileName;
    private String errorMsg;
    private Long createTime;
    private Long startTime;
    private Long finishTime;
}
