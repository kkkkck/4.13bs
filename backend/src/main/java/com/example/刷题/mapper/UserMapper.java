package com.example.刷题.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.刷题.entity.User;

public interface UserMapper extends BaseMapper<User> {

    User findByEmail(String email);

    User findByNickname(String nickname);
}
