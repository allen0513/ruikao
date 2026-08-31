package com.ruikao.server.service;

import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.dto.StudentDTO;
import com.ruikao.pojo.dto.StudentPageQueryDTO;
import com.ruikao.pojo.entity.Student;

public interface StudentService {

    PageResult<Student> pageQuery(StudentPageQueryDTO dto);

    void add(StudentDTO dto);

    void update(StudentDTO dto);

    void delete(Long id);

    Student getById(Long id);

    /**
     * 更新学生头像
     * @param id 学生ID
     * @param avatar 头像完整 URL
     */
    void updateAvatar(Long id, String avatar);

    Student findByStudentNo(String studentNo);
}
