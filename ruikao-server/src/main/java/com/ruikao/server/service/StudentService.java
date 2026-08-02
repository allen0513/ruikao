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

    /**
     * 微信登录：通过 code 获取 openid，查找或绑定学生
     * @return 已绑定 openid 的学生，null 表示未绑定
     */
    Student wxLogin(String code);

    /**
     * 绑定微信 openid 到已有学生账号
     * @param code 微信临时 code
     * @param studentNo 学号
     * @param password 密码
     * @return 绑定后的学生
     */
    Student bindWx(String code, String studentNo, String password);
}
