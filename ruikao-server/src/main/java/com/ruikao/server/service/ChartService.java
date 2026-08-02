package com.ruikao.server.service;

import com.ruikao.pojo.vo.DashboardVO;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.Map;

public interface ChartService {

    DashboardVO getDashboard();

    Map<String, Object> getExamTrend();

    Map<String, Object> getPassRate();

    /**
     * 构建数据统计 Excel 报表（多 sheet）
     */
    XSSFWorkbook buildStatisticWorkbook();
}
