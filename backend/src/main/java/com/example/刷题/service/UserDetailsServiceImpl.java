package com.example.刷题.service;

import com.example.刷题.entity.User;
import com.example.刷题.mapper.UserMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    public UserDetailsServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userMapper.selectById(Long.parseLong(userId));
        if (user == null) {
            throw new UsernameNotFoundException("User not found with id: " + userId);
        }
        
        // 根据用户角色设置权限
        String role = user.getRole() == 1 ? "ROLE_ADMIN" : "ROLE_USER";
        
        return new org.springframework.security.core.userdetails.User(
                userId,
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority(role))
        );
    }
}