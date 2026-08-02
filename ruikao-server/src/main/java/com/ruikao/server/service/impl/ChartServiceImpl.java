package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruikao.pojo.entity.Exam;
import com.ruikao.pojo.entity.ExamRecord;
import com.ruikao.pojo.entity.Student;
import com.ruikao.pojo.entity.SysUser;
import com.ruikao.pojo.vo.DashboardVO;
import com.ruikao.server.mapper.ExamMapper;
import com.ruikao.server.mapper.ExamRecordMapper;
import com.ruikao.server.mapper.StudentMapper;
import com.ruikao.server.mapper.SysUserMapper;
import com.ruikao.server.service.ChartService;
import org.apache.poi.ss.usermodel.*;
import org.springframework.cache.annotation.Cacheable;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChartServiceImpl implements ChartService {

    @Autowired
    private ExamMapper examMapper;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private ExamRecordMapper examRecordMapper;

    @Override
    @Cacheable(cacheNames = "chart", key = "'dashboard'")
    public DashboardVO getDashboard() {
        DashboardVO vo = new DashboardVO();

        // 考试总数
        Long totalExams = examMapper.selectCount(null);
        vo.setTotalExams(totalExams != null ? totalExams : 0L);

        // 进行中的考试（status=1）
        LambdaQueryWrapper<Exam> activeExamWrapper = new LambdaQueryWrapper<>();
        activeExamWrapper.eq(Exam::getStatus, 1);
        Long activeExams = examMapper.selectCount(activeExamWrapper);
        vo.setActiveExams(activeExams != null ? activeExams : 0L);

        // 学生总数
        Long totalStudents = studentMapper.selectCount(null);
        vo.setTotalStudents(totalStudents != null ? totalStudents : 0L);

        // 教师总数
        LambdaQueryWrapper<SysUser> teacherWrapper = new LambdaQueryWrapper<>();
        teacherWrapper.eq(SysUser::getUserType, 2);
        Long totalTeachers = sysUserMapper.selectCount(teacherWrapper);
        vo.setTotalTeachers(totalTeachers != null ? totalTeachers : 0L);

        // 待阅卷（status=2 已提交未阅卷）
        LambdaQueryWrapper<ExamRecord> pendingWrapper = new LambdaQueryWrapper<>();
        pendingWrapper.eq(ExamRecord::getStatus, 2);
        Long pendingMark = examRecordMapper.selectCount(pendingWrapper);
        vo.setPendingMark(pendingMark != null ? pendingMark : 0L);

        // 平均分 & 总通过率
        List<ExamRecord> allRecords = examRecordMapper.selectList(null);
        long totalRecords = allRecords.size();

        long passedRecords = allRecords.stream()
                .filter(r -> r.getScore() != null && r.getScore().compareTo(BigDecimal.valueOf(60)) >= 0)
                .count();
        double passRate = totalRecords > 0 ? (double) passedRecords / totalRecords * 100 : 0.0;
        vo.setPassRate(Math.round(passRate * 10.0) / 10.0);

        double avgScore = allRecords.stream()
                .filter(r -> r.getScore() != null)
                .mapToDouble(r -> r.getScore().doubleValue())
                .average()
                .orElse(0.0);
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
        List<Exam> exams = examMapper.selectList(null);

        // 按月份分组统计考试数量
        Map<String, Long> trendMap = exams.stream()
                .filter(e -> e.getCreateTime() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getCreateTime().toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")),
                        Collectors.counting()
                ));

        // 按月份排序
        List<String> months = trendMap.keySet().stream().sorted().collect(Collectors.toList());

        // 返回前端期望的格式: [{month, count}]
        List<Map<String, Object>> list = months.stream().map(m -> {
            Map<String, Object> item = new HashMap<>();
            item.put("month", m);
            item.put("count", trendMap.get(m));
            return item;
        }).collect(Collectors.toList());

        result.put("data", list);
        // 兼容旧格式
        result.put("dates", months);
        result.put("counts", months.stream().map(trendMap::get).collect(Collectors.toList()));
        return result;
    }

    @Override
    @Cacheable(cacheNames = "chart", key = "'passRate'")
    public Map<String, Object> getPassRate() {
        Map<String, Object> result = new HashMap<>();
        List<ExamRecord> records = examRecordMapper.selectList(null);

        Map<String, Long> passMap = records.stream()
                .filter(r -> r.getScore() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getScore().compareTo(BigDecimal.valueOf(60)) >= 0 ? "pass" : "fail",
                        Collectors.counting()
                ));

        long pass = passMap.getOrDefault("pass", 0L);
        long fail = passMap.getOrDefault("fail", 0L);
        long total = pass + fail;

        result.put("passCount", pass);
        result.put("failCount", fail);
        result.put("totalCount", total);
        result.put("passRate", total > 0 ? Math.round((double) pass / total * 1000) / 10.0 : 0.0);
        return result;
    }

    @Override
    public XSSFWorkbook buildStatisticWorkbook() {
        // 先取数：任何查询异常在此抛出，由全局异常处理器返回 JSON 错误
        DashboardVO dashboard = getDashboard();
        Map<String, Object> trend = getExamTrend();
        Map<String, Object> pass = getPassRate();

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
        switch (String.valueOf(status)) {
            case "0": return "未开始";
            case "1": return "进行中";
            case "2": return "已结束";
            default: return "未知";
        }
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
            int total = records.size();
            map.put("studentCount", total);

            if (total > 0) {
                long pass = records.stream()
                        .filter(r -> r.getScore() != null && r.getScore().compareTo(BigDecimal.valueOf(60)) >= 0)
                        .count();
                double rate = Math.round((double) pass / total * 1000) / 10.0;
                map.put("passRate", rate + "%");
            } else {
                map.put("passRate", "-");
            }

            return map;
        }).collect(Collectors.toList());
    }
}