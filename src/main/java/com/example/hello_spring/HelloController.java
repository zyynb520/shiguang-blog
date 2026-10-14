package com.example.hello_spring;

import com.example.hello_spring.common.Auth;
import com.example.hello_spring.common.Result;
import com.example.hello_spring.entity.User;
import com.example.hello_spring.mapper.UserMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class HelloController {

    @Autowired
    private UserMapper userMapper;

    @GetMapping("/hello")
    public String hello() {
        return "你好，Spring Boot！这是我的第一个接口！";
    }

    /**
     * 调试接口：全部用户。
     * 开放注册后这个接口会泄露所有用户名，所以改成只允许管理员访问。
     */
    @GetMapping("/users")
    public Result<List<User>> getUsers(HttpSession session) {
        if (!Auth.isAdmin(Auth.current(session))) {
            return Result.error("只有站长可以查看用户列表");
        }
        List<User> users = userMapper.findAll();
        // 调试接口也不返回密码摘要
        users.forEach(user -> user.setPassword(null));
        return Result.success(users);
    }
}
