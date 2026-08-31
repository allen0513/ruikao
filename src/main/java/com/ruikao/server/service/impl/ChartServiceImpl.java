package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruikao.common.constant.ExamConstants;
import com.ruikao.pojo.entity.Exam;
import com.ruikao.pojo.entity.ExamRecord;
import com.ruikao.pojo.entity.SysUser;
import com.ruikao.pojo.vo.DashboardVO;
import com.ruikao.server.mapper.ExamMapper;
import com.ruikao.server.mapper.ExamRecordMapper;
import com.ruikao.server.mapper.StudentMapper;
import com.ruikao.server.mapper.SysUserMapper;
import com.ruikao.server.service.ChartService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.cache.annotation.Cacheable;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChartServiceImpl implements ChartService {

    private final ExamMapper examMapper;

    private final StudentMapper studentMapper;

    private final SysUserMapper sysUserMapper;

    private final ExamRecordMapper examRecordMapper;

    /**
     * 自身代理引用：Excel 导出时经代理调用 @Cacheable 方法，避免类内自调用绕过缓存
     */
    private final ObjectProvider<ChartService> selfProvider;

    @Override
    @Cacheable(cacheNames = "chart", key = "'dashboard'")
    public DashboardVO getDashboard() {
        DashboardVO vo = new DashboardVO();

        // 考试总数
        Long totalExams = examMapper.selectCount(null);
        vo.setTotalExams(totalExams != null ? totalExams : 0L);

        // 进行中的考试
        LambdaQueryWrapper<Exam> activeExamWrapper = new LambdaQueryWrapper<>();
        activeExamWrapper.eq(Exam::getStatus, ExamConstants.STATUS_IN_PROGRESS);
        Long activeExams = examMapper.selectCount(activeExamWrapper);
        vo.setActiveExams(activeExams != null ? activeExams : 0L);

        // 学生总数
        Long totalStudents = studentMapper.selectCount(null);
        vo.setTotalStudents(totalStudents != null ? totalStudents : 0L);

        // 教师总数（user_type=1 为教师，曾误用 2 导致恒为 0）
        LambdaQueryWrapper<SysUser> teacherWrapper = new LambdaQueryWrapper<>();
        teacherWrapper.eq(SysUser::getUserType, ExamConstants.USER_TYPE_TEACHER);
        Long totalTeachers = sysUserMapper.selectCount(teacherWrapper);
        vo.setTotalTeachers(totalTeachers != null ? totalTeachers : 0L);

        // 待阅卷（status=2 已提交未阅卷）
        LambdaQueryWrapper<ExamRecord> pendingWrapper = new LambdaQueryWrapper<>();
        pendingWrapper.eq(ExamRecord::getStatus, 2);
        Long pendingMark = examRecordMapper.selectCount(pendingWrapper);
        vo.setPendingMark(pendingMark != null ? pendingMark : 0L);

        // 平均分 & 总通过率（SQL 聚合，口径：已出分记录，score 非空）
        Long totalRecords = examRecordMapper.selectCountWithScore();
        Long passedRecords = examRecordMapper.selectPassCount();
        BigDecimal avgScoreValue = examRecordMapper.selectAvgScore();

        long total = totalRecords != null ? totalRecords : 0L;
        long passed = passedRecords != null ? passedRecords : 0L;
        double passRate = total > 0 ? (double) passed / total * 100 : 0.0;
        vo.setPassRate(Math.round(passRate * 10.0) / 10.0);

        double avgScore = avgScoreValue != null ? avgScoreValue.doubleValue() : 0.0;
        vo.setAvgScore(Math.round(avgScore * 10.0) / 10.0);

        // 最近5场考试
        List<Map<String, Object>> recentExams = getRecentExamList();
        vo.setRecentExams(recentExams);

        return vo;
    }

    @Override
    @Cacheable(cacheNames = "chart", key = "'examTrend'")
    public Map<String, Object> getExamTrend() {
        Map<String, Object> result = new HashMap<>();
        // SQL 聚合按月统计，避免全表加载
        List<Map<String, Object>> rows = examMapper.selectExamTrendByMonth();

        // 返回前端期望的格式: [{month, count}]
        List<Map<String, Object>> list = rows.stream().map(r -> {
            Map<String, Object> item = new HashMap<>();
            item.put("month", r.get("month"));
            item.put("count", r.get("cnt"));
            return item;
        }).collect(Collectors.toList());

        result.put("data", list);
        return result;
    }

    @Override
    @Cacheable(cacheNames = "chart", key = "'passRate'")
    public Map<String, Object> getPassRate() {
        Map<String, Object> result = new HashMap<>();
        // SQL 聚合统计（口径与 dashboard 一致：已出分记录）
        Long total = examRecordMapper.selectCountWithScore();
        Long pass = examRecordMapper.selectPassCount();

        long totalCount = total != null ? total : 0L;
        long passCount = pass != null ? pass : 0L;
        long failCount = totalCount - passCount;

        result.put("passCount", passCount);
        result.put("failCount", failCount);
        result.put("totalCount", totalCount);
        result.put("passRate", totalCount > 0 ? Math.round((double) passCount / totalCount * 1000) / 10.0 : 0.0);
        return result;
    }

    /**
     * 异步预热图表统计缓存：经自身代理调用 @Cacheable 方法把最新统计写入缓存，
     * 重算在后台线程执行，不阻塞成绩定稿/自动收卷等主流程；失败仅记日志，不影响业务
     */
    @Override
    @Async("chartAsyncExecutor")
    public void refreshCacheAsync() {
        try {
            ChartService self = selfProvider.getObject();
            self.getDashboard();
            self.getExamTrend();
            self.getPassRate();
            log.info("图表统计缓存已异步刷新");
        } catch (Exception e) {
            log.error("异步刷新图表统计缓存失败", e);
        }
    }

    @Override
    public XSSFWorkbook buildStatisticWorkbook() {
        // 先取数：任何查询异常在此抛出，由全局异常处理器返回 JSON 错误
        // 经自身代理调用，走 @Cacheable 缓存（类内直接调用会绕过 Spring 代理）
        ChartService self = selfProvider.getObject();
        DashboardVO dashboard = self.getDashboard();
        Map<String, Object> trend = self.getExamTrend();
        Map<String, Object> pass = self.getPassRate();

        // 再构建 workbook（纯内存构建，成功后才交由 Controller 写出）
        XSSFWorkbook wb = new XSSFWorkbook();
        CellStyle headerStyle = createHeaderStyle(wb);
        createOverviewSheet(wb, headerStyle, dashboard);
        createTrendSheet(wb, headerStyle, trend);
        createPassRateSheet(wb, headerStyle, pass);
        createRecentExamSheet(wb, headerStyle, dashboard.getRecentExams());
        return wb;
    }

    private CellStyle createHeaderStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void writeHeader(XSSFSheet sheet, CellStyle headerStyle, String... headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        sheet.createFreezePane(0, 1);
    }

    /** Sheet1 统计概览 */
    private void createOverviewSheet(XSSFWorkbook wb, CellStyle headerStyle, DashboardVO dashboard) {
        XSSFSheet sheet = wb.createSheet("统计概览");
        writeHeader(sheet, headerStyle, "指标", "数值");
        String[][] rows = {
                {"考试总数", String.valueOf(dashboard.getTotalExams())},
                {"进行中考试", String.valueOf(dashboard.getActiveExams())},
                {"学生总数", String.valueOf(dashboard.getTotalStudents())},
                {"教师总数", String.valueOf(dashboard.getTotalTeachers())},
                {"待阅卷记录", String.valueOf(dashboard.getPendingMark())},
                {"平均分", String.valueOf(dashboard.getAvgScore())},
                {"总通过率", dashboard.getPassRate() + "%"},
                {"生成时间", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
        };
        int rowIndex = 1;
        for (String[] rowData : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(rowData[0]);
            row.createCell(1).setCellValue(rowData[1]);
        }
        sheet.setColumnWidth(0, 18 * 256);
        sheet.setColumnWidth(1, 30 * 256);
    }

    /** Sheet2 月度考试趋势 */
    private void createTrendSheet(XSSFWorkbook wb, CellStyle headerStyle, Map<String, Object> trend) {
        XSSFSheet sheet = wb.createSheet("月度考试趋势");
        writeHeader(sheet, headerStyle, "月份", "考试数量");
        Object data = trend.get("data");
        if (data instanceof List) {
            int rowIndex = 1;
            for (Object item : (List<?>) data) {
                if (item instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) item;
                    Row row = sheet.createRow(rowIndex++);
                    row.createCell(0).setCellValue(String.valueOf(m.get("month")));
                    row.createCell(1).setCellValue(((Number) m.get("count")).doubleValue());
                }
            }
        }
        sheet.setColumnWidth(0, 16 * 256);
        sheet.setColumnWidth(1, 16 * 256);
    }

    /** Sheet3 考试通过率 */
    private void createPassRateSheet(XSSFWorkbook wb, CellStyle headerStyle, Map<String, Object> pass) {
        XSSFSheet sheet = wb.createSheet("考试通过率");
        writeHeader(sheet, headerStyle, "通过人数", "未通过人数", "参考总数", "通过率(%)");
        Row row = sheet.createRow(1);
        row.createCell(0).setCellValue(((Number) pass.get("passCount")).doubleValue());
        row.createCell(1).setCellValue(((Number) pass.get("failCount")).doubleValue());
        row.createCell(2).setCellValue(((Number) pass.get("totalCount")).doubleValue());
        row.createCell(3).setCellValue(((Number) pass.get("passRate")).doubleValue());
        for (int i = 0; i < 4; i++) {
            sheet.setColumnWidth(i, 14 * 256);
        }
    }

    /** Sheet4 最近考试明细 */
    private void createRecentExamSheet(XSSFWorkbook wb, CellStyle headerStyle, List<Map<String, Object>> recentExams) {
        XSSFSheet sheet = wb.createSheet("最近考试明细");
        writeHeader(sheet, headerStyle, "考试名称", "课程", "考试日期", "开始时间", "结束时间", "考场", "状态", "参考人数", "通过率");
        if (recentExams != null) {
            int rowIndex = 1;
            for (Map<String, Object> exam : recentExams) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(safeStr(exam.get("examName")));
                row.createCell(1).setCellValue(safeStr(exam.get("courseName")));
                row.createCell(2).setCellValue(safeStr(exam.get("examDate")));
                row.createCell(3).setCellValue(safeStr(exam.get("startTime")));
                row.createCell(4).setCellValue(safeStr(exam.get("endTime")));
                row.createCell(5).setCellValue(safeStr(exam.get("examRoom")));
                row.createCell(6).setCellValue(statusText(exam.get("status")));
                row.createCell(7).setCellValue(((Number) exam.get("studentCount")).doubleValue());
                row.createCell(8).setCellValue(safeStr(exam.get("passRate")));
            }
        }
        int[] widths = {26, 16, 14, 12, 12, 16, 12, 12, 12};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private String safeStr(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String statusText(Object status) {
        if (status == null) return "未知";
        if (status instanceof Number) {
            switch (((Number) status).intValue()) {
                case ExamConstants.STATUS_NOT_STARTED: return "未开始";
                case ExamConstants.STATUS_IN_PROGRESS: return "进行中";
                case ExamConstants.STATUS_FINISHED: return "已结束";
                default: return "未知";
            }
        }
        return "未知";
    }

    /**
     * 获取最近5场考试及参考人数、通过率
     */
    private List<Map<String, Object>> getRecentExamList() {
        LambdaQueryWrapper<Exam> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Exam::getCreateTime);
        wrapper.last("LIMIT 5");
        List<Exam> exams = examMapper.selectList(wrapper);

        return exams.stream().map(exam -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", exam.getId());
            map.put("examName", exam.getExamName());
            map.put("courseName", exam.getCourseName());
            map.put("examDate", exam.getExamDate());
            map.put("startTime", exam.getStartTime());
            map.put("endTime", exam.getEndTime());
            map.put("examRoom", exam.getExamRoom());
            map.put("status", exam.getStatus());

            // 统计该考试的参考人数和通过率
            LambdaQueryWrapper<ExamRecord> recordWrapper = new LambdaQueryWrapper<>();
            recordWrapper.eq(ExamRecord::getExamId, exam.getId());
            List<ExamRecord> records = examRecordMapper.selectList(recordWrapper);
            map.put("studentCount", records.size());

            // 通过率口径与全局一致：分母为已出分记录（score 非空），避免含未出分记录稀释通过率
            List<ExamRecord> scoredRecords = records.stream()
                    .filter(r -> r.getScore() != null)
                    .collect(Collectors.toList());
            if (!scoredRecords.isEmpty()) {
                long pass = scoredRecords.stream()
                        .filter(r -> r.getScore().compareTo(BigDecimal.valueOf(ExamConstants.PASS_LINE)) >= 0)
                        .count();
                double rate = Math.round((double) pass / scoredRecords.size() * 1000) / 10.0;
                map.put("passRate", rate + "%");
            } else {
                map.put("passRate", "-");
            }

            return map;
        }).collect(Collectors.toList());
    }
}