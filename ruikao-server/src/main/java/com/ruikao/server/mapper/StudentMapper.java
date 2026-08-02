package com.ruikao.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruikao.pojo.entity.Student;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface StudentMapper extends BaseMapper<Student> {

    @Select("SELECT * FROM student WHERE student_no = #{studentNo}")
    Student findByStudentNo(@Param("studentNo") String studentNo);

    @Select("SELECT * FROM student WHERE openid = #{openid}")
    Student findByOpenid(@Param("openid") String openid);
}
