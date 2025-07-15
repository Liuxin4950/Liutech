package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    public List<User> findAll() {
        return userMapper.selectList(null);
    }

    public void addUser(User user) {
        userMapper.insert(user);
    }

    public void deleteById(Long id) {
        userMapper.deleteById(id);
    }
    public List<User> findByUserName(String userName) {
        return userMapper.findByUserName(userName);
    }

}
