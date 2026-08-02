package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruikao.common.constant.ExamConstants;
import com.ruikao.common.context.BaseContext;
import com.ruikao.common.exception.AccountNotFoundException;
import com.ruikao.common.exception.BusinessException;
import com.ruikao.common.exception.PasswordErrorException;
import com.ruikao.common.result.PageResult;
import com.ruikao.common.utils.PasswordUtil;
import com.ruikao.common.utils.WeChatUtil;
import com.ruikao.pojo.dto.StudentDTO;
import com.ruikao.pojo.dto.StudentPageQueryDTO;
import com.ruikao.pojo.entity.ExamRecord;
import com.ruikao.pojo.entity.ExamStudent;
import com.ruikao.pojo.entity.Student;
import com.ruikao.server.mapper.ExamRecordMapper;
import com.ruikao.server.mapper.ExamStudentMapper;
import com.ruikao.server.mapper.StudentMapper;
import com.ruikao.server.service.StudentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentMapper studentMapper;

    private final ExamRecordMapper examRecordMapper;

    private final ExamStudentMapper examStudentMapper;

    private final WeChatUtil weChatUtil;

    @Override
    public PageResult<Student> pageQuery(StudentPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        if (dto.getStudentNo() != null && !dto.getStudentNo().isEmpty()) {
            wrapper.like(Student::getStudentNo, dto.getStudentNo());
        }
        if (dto.getName() != null && !dto.getName().isEmpty()) {
            wrapper.like(Student::getName, dto.getName());
        }
        if (dto.getClassName() != null && !dto.getClassName().isEmpty()) {
            wrapper.like(Student::getClassName, dto.getClassName());
        }
        // 关键字：模糊匹配姓名或学号（嵌套 and 保证优先级正确）
        if (dto.getKeyword() != null && !dto.getKeyword().isEmpty()) {
            wrapper.and(w -> w.like(Student::getName, dto.getKeyword())
                    .or().like(Student::getStudentNo, dto.getKeyword()));
        }
        wrapper.orderByDesc(Student::getCreateTime);
        List<Student> list = studentMapper.selectList(wrapper);
        Page<Student> page = (Page<Student>) list;
        return PageResult.of(page.getTotal(), page.getResult());
    }

    @Override
    public void add(StudentDTO dto) {
        // 新增时密码必填
        if (dto.getPassword() == null || dto.getPassword().isEmpty()) {
            throw new BusinessException("初始密码不能为空");
        }
        // 越权防护：仅管理员可创建时直接禁用学生，防止教师 token 提权操作
        checkStatusPrivilege(dto.getStatus());

        Student student = new Student();
        BeanUtils.copyProperties(dto, student);
        // 未指定状态时收口为启用
        if (student.getStatus() == null) {
            student.setStatus(ExamConstants.USER_STATUS_ENABLED);
        }
        // 密码加密后入库，禁止明文存储
        student.setPassword(PasswordUtil.encode(dto.getPassword()));
        studentMapper.insert(student);
    }

    @Override
    public void update(StudentDTO dto) {
        // 越权防护：账号状态变化（启用/禁用/复活）仅管理员可操作
        Integer currentStatus = null;
        if (dto.getStatus() != null && dto.getId() != null) {
            Student exist = studentMapper.selectById(dto.getId());
            currentStatus = exist != null ? exist.getStatus() : null;
        }
        boolean statusChanged = dto.getStatus() != null && !dto.getStatus().equals(currentStatus);
        checkStatusPrivilege(statusChanged ? dto.getStatus() : null);

        Student student = new Student();
        BeanUtils.copyProperties(dto, student);
        // 密码字段非空时重新加密，避免明文覆盖；为空则置 null 不更新（防止空串写库）
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            student.setPassword(PasswordUtil.encode(dto.getPassword()));
        } else {
            student.setPassword(null);
        }
        studentMapper.updateById(student);
    }

    @Override
    public void delete(Long id) {
        // 已有考试记录的学生禁止删除，防止成绩数据悬空
        LambdaQueryWrapper<ExamRecord> recordWrapper = new LambdaQueryWrapper<>();
        recordWrapper.eq(ExamRecord::getStudentId, id);
        if (examRecordMapper.selectCount(recordWrapper) > 0) {
            throw new BusinessException("该学生已有考试记录，无法删除");
        }
        // 清理该学生的考试分配关系
        LambdaQueryWrapper<ExamStudent> esWrapper = new LambdaQueryWrapper<>();
        esWrapper.eq(ExamStudent::getStudentId, id);
        examStudentMapper.delete(esWrapper);
        studentMapper.deleteById(id);
    }

    @Override
    public Student getById(Long id) {
        return studentMapper.selectById(id);
    }

    @Override
    public void updateAvatar(Long id, String avatar) {
        Student student = new Student();
        student.setId(id);
        student.setAvatar(avatar);
        studentMapper.updateById(student);
        log.info("学生头像已更新, id: {}, avatar: {}", id, avatar);
    }

    @Override
    public Student findByStudentNo(String studentNo) {
        return studentMapper.findByStudentNo(studentNo);
    }

    /**
     * 越权防护：仅管理员可变更学生账号状态（禁用/启用），
     * 防止教师 token 通过 mass assignment 复活禁用账号或制造禁用。
     *
     * @param status 请求中的目标状态，null 表示未提交/未变更该字段
     */
    private void checkStatusPrivilege(Integer status) {
        if (status != null && !Integer.valueOf(ExamConstants.USER_TYPE_ADMIN).equals(BaseContext.getCurrentUserType())) {
            throw new BusinessException("仅管理员可修改学生账号状态");
        }
    }

    @Override
    public Student wxLogin(String code) {
        // 通过微信 code 换取 openid
        String openid = weChatUtil.getOpenid(code);
        if (openid == null) {
            throw new BusinessException("微信登录失败，无法获取用户身份");
        }

        // 查找是否已绑定该 openid
        Student student = studentMapper.findByOpenid(openid);
        if (student != null) {
            // 禁用账号禁止微信登录
            if (student.getStatus() != ExamConstants.USER_STATUS_ENABLED) {
                throw new BusinessException("账号已被禁用，请联系管理员");
            }
            log.info("微信登录成功，已绑定的学生: {}, 姓名: {}", student.getStudentNo(), student.getName());
            return student;
        }

        // 未绑定，返回 null 让前端提示绑定（不打印 openid，避免敏感信息落日志）
        log.info("微信 openid 未绑定学生账号，需先完成学号绑定");
        return null;
    }

    @Override
    public Student bindWx(String code, String studentNo, String password) {
        // 通过微信 code 换取 openid
        String openid = weChatUtil.getOpenid(code);
        if (openid == null) {
            throw new BusinessException("微信登录失败，无法获取用户身份");
        }

        // 查找学生
        Student student = studentMapper.findByStudentNo(studentNo);
        if (student == null) {
            throw new AccountNotFoundException("学号不存在");
        }

        // 禁用账号禁止绑定登录
        if (student.getStatus() != ExamConstants.USER_STATUS_ENABLED) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        // 验证密码
        if (!PasswordUtil.matches(password, student.getPassword())) {
            throw new PasswordErrorException("密码错误");
        }

        // 检查该 openid 是否已被其他学生绑定
        Student existBind = studentMapper.findByOpenid(openid);
        if (existBind != null && !existBind.getId().equals(student.getId())) {
            throw new BusinessException("该微信已绑定其他学生账号");
        }

        // 绑定 openid
        student.setOpenid(openid);
        studentMapper.updateById(student);
        log.info("微信绑定成功，学生: {}, 姓名: {}", studentNo, student.getName());

        return student;
    }
}
