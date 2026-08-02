package com.ruikao.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruikao.common.exception.AccountNotFoundException;
import com.ruikao.common.exception.PasswordErrorException;
import com.ruikao.common.result.PageResult;
import com.ruikao.common.utils.PasswordUtil;
import com.ruikao.common.utils.WeChatUtil;
import com.ruikao.pojo.dto.StudentPageQueryDTO;
import com.ruikao.pojo.entity.Student;
import com.ruikao.server.mapper.StudentMapper;
import com.ruikao.server.service.StudentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class StudentServiceImpl implements StudentService {

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private WeChatUtil weChatUtil;

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
        wrapper.orderByDesc(Student::getCreateTime);
        List<Student> list = studentMapper.selectList(wrapper);
        Page<Student> page = (Page<Student>) list;
        return PageResult.of(page.getTotal(), page.getResult());
    }

    @Override
    public void add(Student student) {
        studentMapper.insert(student);
    }

    @Override
    public void update(Student student) {
        studentMapper.updateById(student);
    }

    @Override
    public void delete(Long id) {
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

    @Override
    public Student wxLogin(String code) {
        // 通过微信 code 换取 openid
        String openid = weChatUtil.getOpenid(code);
        if (openid == null) {
            throw new RuntimeException("微信登录失败，无法获取用户身份");
        }

        // 查找是否已绑定该 openid
        Student student = studentMapper.findByOpenid(openid);
        if (student != null) {
            log.info("微信登录成功，已绑定的学生: {}, 姓名: {}", student.getStudentNo(), student.getName());
            return student;
        }

        // 未绑定，返回 null 让前端提示绑定
        log.info("微信 openid 未绑定学生账号: {}", openid);
        return null;
    }

    @Override
    public Student bindWx(String code, String studentNo, String password) {
        // 通过微信 code 换取 openid
        String openid = weChatUtil.getOpenid(code);
        if (openid == null) {
            throw new RuntimeException("微信登录失败，无法获取用户身份");
        }

        // 查找学生
        Student student = studentMapper.findByStudentNo(studentNo);
        if (student == null) {
            throw new AccountNotFoundException("学号不存在");
        }

        // 验证密码
        if (!PasswordUtil.matches(password, student.getPassword())) {
            throw new PasswordErrorException("密码错误");
        }

        // 检查该 openid 是否已被其他学生绑定
        Student existBind = studentMapper.findByOpenid(openid);
        if (existBind != null && !existBind.getId().equals(student.getId())) {
            throw new RuntimeException("该微信已绑定其他学生账号");
        }

        // 绑定 openid
        student.setOpenid(openid);
        studentMapper.updateById(student);
        log.info("微信绑定成功，学生: {}, 姓名: {}", studentNo, student.getName());

        return student;
    }
}
