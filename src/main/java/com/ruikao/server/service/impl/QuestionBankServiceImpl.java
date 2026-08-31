package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruikao.common.constant.ExamConstants;
import com.ruikao.common.context.BaseContext;
import com.ruikao.common.exception.BusinessException;
import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.dto.QuestionDTO;
import com.ruikao.pojo.dto.QuestionPageQueryDTO;
import com.ruikao.pojo.entity.ExamAnswer;
import com.ruikao.pojo.entity.PaperQuestion;
import com.ruikao.pojo.entity.QuestionBank;
import com.ruikao.pojo.entity.QuestionKnowledgePoint;
import com.ruikao.pojo.vo.QuestionImportResultVO;
import com.ruikao.server.mapper.ExamAnswerMapper;
import com.ruikao.server.mapper.PaperQuestionMapper;
import com.ruikao.server.mapper.QuestionBankMapper;
import com.ruikao.server.mapper.QuestionKnowledgePointMapper;
import com.ruikao.server.service.QuestionBankService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionBankServiceImpl implements QuestionBankService {

    private final QuestionBankMapper questionBankMapper;

    private final PaperQuestionMapper paperQuestionMapper;

    private final ExamAnswerMapper examAnswerMapper;

    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;

    /** 当前用户是否为教师（教师仅可修改/删除自己创建的题目） */
    private boolean isTeacher() {
        return Integer.valueOf(ExamConstants.USER_TYPE_TEACHER).equals(BaseContext.getCurrentUserType());
    }

    @Override
    public PageResult<QuestionBank> pageQuery(QuestionPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        LambdaQueryWrapper<QuestionBank> wrapper = new LambdaQueryWrapper<>();
        if (dto.getQuestionType() != null) {
            wrapper.eq(QuestionBank::getQuestionType, dto.getQuestionType());
        }
        if (dto.getDifficulty() != null) {
            wrapper.eq(QuestionBank::getDifficulty, dto.getDifficulty());
        }
        if (dto.getSubjectId() != null) {
            wrapper.eq(QuestionBank::getSubjectId, dto.getSubjectId());
        }
        if (dto.getQuestionContent() != null && !dto.getQuestionContent().isEmpty()) {
            wrapper.like(QuestionBank::getQuestionContent, dto.getQuestionContent());
        }
        wrapper.orderByDesc(QuestionBank::getCreateTime);
        List<QuestionBank> list = questionBankMapper.selectList(wrapper);
        Page<QuestionBank> page = (Page<QuestionBank>) list;
        return PageResult.of(page.getTotal(), page.getResult());
    }

    @Override
    @Transactional
    public void add(QuestionDTO dto) {
        QuestionBank question = new QuestionBank();
        BeanUtils.copyProperties(dto, question);
        question.setCreatorId(BaseContext.getCurrentId());
        questionBankMapper.insert(question);
        // 事务维护题目知识点关联（多对多）
        saveKnowledgePointRelations(question.getId(), dto.getKnowledgePointIds());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "questionBank", key = "#dto.id")
    public void update(QuestionDTO dto) {
        checkOwnership(dto.getId());
        QuestionBank question = new QuestionBank();
        BeanUtils.copyProperties(dto, question);
        questionBankMapper.updateById(question);
        // 知识点关联先清后插，保证与表单勾选状态一致
        deleteKnowledgePointRelations(question.getId());
        saveKnowledgePointRelations(question.getId(), dto.getKnowledgePointIds());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "questionBank", key = "#id")
    public void delete(Long id) {
        checkOwnership(id);
        // 被试卷引用的题目禁止删除，防止外键级联静默改写已发布试卷
        LambdaQueryWrapper<PaperQuestion> pqWrapper = new LambdaQueryWrapper<>();
        pqWrapper.eq(PaperQuestion::getQuestionId, id);
        if (paperQuestionMapper.selectCount(pqWrapper) > 0) {
            throw new BusinessException("该题目已被试卷引用，无法删除");
        }
        // 已有作答记录的题目禁止删除，防止答卷数据失效
        LambdaQueryWrapper<ExamAnswer> answerWrapper = new LambdaQueryWrapper<>();
        answerWrapper.eq(ExamAnswer::getQuestionId, id);
        if (examAnswerMapper.selectCount(answerWrapper) > 0) {
            throw new BusinessException("该题目已有作答记录，无法删除");
        }
        // 清理题目知识点关联，避免残留悬空数据
        deleteKnowledgePointRelations(id);
        questionBankMapper.deleteById(id);
    }

    @Override
    @Cacheable(cacheNames = "questionBank", key = "#id")
    public QuestionBank getDetail(Long id) {
        return questionBankMapper.selectById(id);
    }

    @Override
    public List<Long> getKnowledgePointIds(Long questionId) {
        LambdaQueryWrapper<QuestionKnowledgePoint> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionKnowledgePoint::getQuestionId, questionId);
        return questionKnowledgePointMapper.selectList(wrapper).stream()
                .map(QuestionKnowledgePoint::getKnowledgePointId)
                .collect(Collectors.toList());
    }

    /**
     * Excel 批量导入题库：模板为「题型 | 题目内容 | 选项 | 答案 | 解析 | 分值 | 难度 | 科目ID | 知识点ID」，
     * 题型支持 0-5 数字或中文名称，知识点ID 多个用英文逗号分隔。
     * 逐行校验（含表头校验），合法行同一事务批量入库，非法行跳过并记录行号与原因。
     */
    @Override
    @Transactional
    public QuestionImportResultVO importExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException("仅支持 .xlsx 格式的 Excel 文件");
        }

        List<QuestionDTO> batch = new ArrayList<>();
        QuestionImportResultVO result = new QuestionImportResultVO();
        try (XSSFWorkbook wb = new XSSFWorkbook(file.getInputStream())) {
            XSSFSheet sheet = wb.getSheetAt(0);
            if (sheet.getLastRowNum() < 1) {
                throw new BusinessException("文件内容为空，请按模板填写后重新上传");
            }
            checkHeader(sheet.getRow(0));

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowBlank(row)) {
                    continue;
                }
                try {
                    batch.add(parseRow(row));
                } catch (BusinessException e) {
                    result.getErrors().add(new QuestionImportResultVO.RowError(i + 1, e.getMessage()));
                }
            }

            if (batch.isEmpty()) {
                throw new BusinessException("没有可导入的有效数据，请按错误提示修正后重新上传");
            }
            for (QuestionDTO dto : batch) {
                add(dto);
            }
        } catch (IOException e) {
            throw new BusinessException("文件读取失败: " + e.getMessage());
        }
        result.setSuccessCount(batch.size());
        result.setFailCount(result.getErrors().size());
        return result;
    }

    /** 校验模板表头（前 9 列名称必须与模板一致，防错传文件） */
    private void checkHeader(Row header) {
        String[] expected = {"题型", "题目内容", "选项", "答案", "解析", "分值", "难度", "科目ID", "知识点ID"};
        for (int i = 0; i < expected.length; i++) {
            String actual = cellText(header.getCell(i));
            if (!expected[i].equals(actual)) {
                throw new BusinessException("模板表头不匹配：第" + (i + 1) + "列应为「" + expected[i]
                        + "」，实际为「" + (actual.isEmpty() ? "空" : actual) + "」，请按模板填写");
            }
        }
    }

    /** 解析单行数据为 QuestionDTO，校验失败抛 BusinessException（行号由调用方记录） */
    private QuestionDTO parseRow(Row row) {
        QuestionDTO dto = new QuestionDTO();

        Integer type = parseQuestionType(cellText(row.getCell(0)));
        if (type == null) {
            throw new BusinessException("题型不合法（0-5 或 单选题/多选题/判断题/简答题/填空题/操作题）");
        }
        dto.setQuestionType(type);

        String content = cellText(row.getCell(1));
        if (content.isEmpty()) {
            throw new BusinessException("题目内容不能为空");
        }
        dto.setQuestionContent(content);

        dto.setOptions(blankToNull(cellText(row.getCell(2))));
        dto.setAnswer(blankToNull(cellText(row.getCell(3))));
        dto.setAnalysis(blankToNull(cellText(row.getCell(4))));

        String score = cellText(row.getCell(5));
        if (!score.isEmpty()) {
            try {
                dto.setScore(new BigDecimal(score));
            } catch (NumberFormatException e) {
                throw new BusinessException("分值必须为数字");
            }
        }

        String difficulty = cellText(row.getCell(6));
        if (!difficulty.isEmpty()) {
            try {
                int d = Integer.parseInt(difficulty);
                if (d < 1 || d > 5) {
                    throw new BusinessException("难度需在 1~5 之间");
                }
                dto.setDifficulty(d);
            } catch (NumberFormatException e) {
                throw new BusinessException("难度必须为数字（1~5）");
            }
        }

        String subject = cellText(row.getCell(7));
        if (!subject.isEmpty()) {
            try {
                dto.setSubjectId(Long.parseLong(subject));
            } catch (NumberFormatException e) {
                throw new BusinessException("科目ID必须为数字");
            }
        }

        String kpIds = cellText(row.getCell(8));
        if (!kpIds.isEmpty()) {
            try {
                dto.setKnowledgePointIds(Arrays.stream(kpIds.split("[,，]"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Long::parseLong)
                        .collect(Collectors.toList()));
            } catch (NumberFormatException e) {
                throw new BusinessException("知识点ID必须为数字，多个用逗号分隔");
            }
        }
        return dto;
    }

    /** 题型解析：支持 0-5 数字或中文名称 */
    private Integer parseQuestionType(String text) {
        if (text.isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(text);
            if (value >= ExamConstants.QUESTION_TYPE_SINGLE && value <= ExamConstants.QUESTION_TYPE_OPERATION) {
                return value;
            }
            return null;
        } catch (NumberFormatException ignored) {
            // 数字解析失败落到中文名称匹配
        }
        switch (text) {
            case "单选题": return ExamConstants.QUESTION_TYPE_SINGLE;
            case "多选题": return ExamConstants.QUESTION_TYPE_MULTIPLE;
            case "判断题": return ExamConstants.QUESTION_TYPE_JUDGE;
            case "简答题": return ExamConstants.QUESTION_TYPE_SUBJECTIVE;
            case "填空题": return ExamConstants.QUESTION_TYPE_FILL_BLANK;
            case "操作题": return ExamConstants.QUESTION_TYPE_OPERATION;
            default: return null;
        }
    }

    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    /** 读取单元格文本（数字/公式单元格按展示值格式化），空单元格返回空串 */
    private String cellText(Cell cell) {
        return cell == null ? "" : DATA_FORMATTER.formatCellValue(cell).trim();
    }

    private String blankToNull(String text) {
        return text.isEmpty() ? null : text;
    }

    /** 行内 9 列是否全部为空（跳过模板中的空行） */
    private boolean isRowBlank(Row row) {
        for (int i = 0; i < 9; i++) {
            if (!cellText(row.getCell(i)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 批量写入题目知识点关联（去重） */
    private void saveKnowledgePointRelations(Long questionId, List<Long> knowledgePointIds) {
        if (CollectionUtils.isEmpty(knowledgePointIds)) {
            return;
        }
        for (Long kpId : knowledgePointIds.stream().distinct().collect(Collectors.toList())) {
            if (kpId == null) {
                continue;
            }
            QuestionKnowledgePoint qkp = new QuestionKnowledgePoint();
            qkp.setQuestionId(questionId);
            qkp.setKnowledgePointId(kpId);
            questionKnowledgePointMapper.insert(qkp);
        }
    }

    private void deleteKnowledgePointRelations(Long questionId) {
        LambdaQueryWrapper<QuestionKnowledgePoint> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionKnowledgePoint::getQuestionId, questionId);
        questionKnowledgePointMapper.delete(wrapper);
    }

    /**
     * 越权防护：教师只能修改/删除自己创建的题目
     */
    private void checkOwnership(Long id) {
        if (!isTeacher()) {
            return;
        }
        QuestionBank exist = questionBankMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("题目不存在");
        }
        if (!BaseContext.getCurrentId().equals(exist.getCreatorId())) {
            throw new BusinessException("无权操作他人创建的题目");
        }
    }
}
