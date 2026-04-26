CREATE DATABASE IF NOT EXISTS app_monitor DEFAULT CHARSET utf8mb4;
USE app_monitor;

CREATE TABLE employees (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL COMMENT '姓名',
    employee_no VARCHAR(30) UNIQUE NOT NULL COMMENT '工号',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE devices (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id BIGINT NOT NULL COMMENT '绑定员工',
    device_name VARCHAR(100) COMMENT '电脑名称',
    mac_address VARCHAR(50) UNIQUE NOT NULL COMMENT 'MAC地址',
    last_online DATETIME COMMENT '最后上线时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (employee_id) REFERENCES employees(id)
);

CREATE TABLE app_categories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_name VARCHAR(100) NOT NULL COMMENT '应用名称',
    category VARCHAR(50) NOT NULL COMMENT '分类：工作/通讯/娱乐/其他',
    is_work TINYINT(1) DEFAULT 0 COMMENT '是否算作工作应用'
);

CREATE TABLE app_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_id BIGINT NOT NULL,
    app_name VARCHAR(100) NOT NULL,
    window_title VARCHAR(255),
    duration_seconds INT NOT NULL,
    record_date DATE NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id)
);

CREATE TABLE daily_reports (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id BIGINT NOT NULL,
    report_date DATE NOT NULL,
    work_seconds INT DEFAULT 0,
    idle_seconds INT DEFAULT 0,
    efficiency_score INT DEFAULT 0,
    ai_comment VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_employee_date (employee_id, report_date),
    FOREIGN KEY (employee_id) REFERENCES employees(id)
);

-- 演示数据
INSERT INTO employees (name, employee_no) VALUES ('张三', 'EMP001'), ('李四', 'EMP002');

INSERT INTO devices (employee_id, device_name, mac_address, last_online)
VALUES (1, '张三的电脑', 'AA:BB:CC:DD:EE:FF', NOW());

INSERT INTO app_categories (app_name, category, is_work) VALUES
('idea64.exe', '开发工具', 1),
('pycharm64.exe', '开发工具', 1),
('code.exe', '开发工具', 1),
('chrome.exe', '浏览器', 0),
('WeChat.exe', '通讯', 0),
('dingtalk.exe', '通讯', 1);