package com.ruikao.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruikao.pojo.entity.Student;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface StudentMapper extends BaseMapper<Student> {

    /** 显式列出字段，避免 SELECT * 隐式带出大字段/未来新增敏感列 */
    String BASE_COLUMNS = "id, student_no, password, name, gender, major, grade, class_name, "
            + "email, phone, avatar, openid, status, create_time, update_time";

    @Select("SELECT " + BASE_COLUMNS + " FROM student WHERE student_no = #{studentNo}")
    Student findByStudentNo(@Param("studentNo") String studentNo);

    @Select("SELECT " + BASE_COLUMNS + " FROM student WHERE openid = #{openid}")
    Student findByOpenid(@Param("openid") String openid);
}
