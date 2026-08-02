package com.ruikao.server.controller.admin;

import com.ruikao.common.result.Result;
import com.ruikao.pojo.vo.DashboardVO;
import com.ruikao.server.service.ChartService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/chart")
@Slf4j
@RequiredArgsConstructor
public class ChartController {

    private static final String EXCEL_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ChartService chartService;

    @GetMapping("/dashboard")
    public Result<DashboardVO> dashboard() {
        log.info("获取仪表盘数据");
        DashboardVO dashboardVO = chartService.getDashboard();
        return Result.success(dashboardVO);
    }

    @GetMapping("/exam-trend")
    public Result<Map<String, Object>> examTrend() {
        log.info("获取考试趋势数据");
        Map<String, Object> data = chartService.getExamTrend();
        return Result.success(data);
    }

    @GetMapping("/pass-rate")
    public Result<Map<String, Object>> passRate() {
        log.info("获取通过率数据");
        Map<String, Object> data = chartService.getPassRate();
        return Result.success(data);
    }

    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        log.info("导出数据统计报表");
        // 先构建 workbook：取数/构建阶段抛异常时响应尚未写入字节，
        // 由全局异常处理器返回 JSON 错误，不会残留下载响应头
        XSSFWorkbook workbook = chartService.buildStatisticWorkbook();
        String fileName = "数据统计报表_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";

        // header 值必须全 ASCII，filename 参数同样使用 URL 编码，
        // 否则 Tomcat 会丢弃含中文的 Content-Disposition 响应头
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setContentType(EXCEL_CONTENT_TYPE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);

        try (workbook) {
            workbook.write(response.getOutputStream());
            response.getOutputStream().flush();
        }
    }
}
