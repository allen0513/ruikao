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

    /**
     * 异步刷新图表统计缓存：成绩数据变化（定稿/自动收卷/考试状态流转）后调用，
     * 后台重新计算 dashboard/趋势/通过率并写入缓存，避免下次访问同步重算
     */
    void refreshCacheAsync();
}
