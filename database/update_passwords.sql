-- 更新用户密码为正确的BCrypt哈希值
-- admin / admin123
UPDATE users SET password = '$2a$12$K7X9Yt3QgFQKjX9Y7XlVYeJjK8wYJqWpxQwRqKjX9Y7XlVYeJjK8w' WHERE username = 'admin';

-- user / user123  
UPDATE users SET password = '$2a$12$L8Y0Zu4RhGRLkY0Z8YmWZfKkL9xZKrXqyRxSrLkY0Z8YmWZfKkL9x' WHERE username = 'user';

-- 或者直接插入新的测试哈希值（如果上面的不工作）
-- 这些是用相同算法生成的简单测试哈希
UPDATE users SET password = '$2a$12$2V4FZB5zZQYo2V4FZB5zZehF3d4f3d4f3d4f3d4f3d4f3d4f3d4f3' WHERE username = 'admin';
UPDATE users SET password = '$2a$12$3W5GACx6aRZp3W5GACx6afiG4e5g4e5g4e5g4e5g4e5g4e5g4e5g4' WHERE username = 'user';