-- 1. Create Database
CREATE DATABASE IF NOT EXISTS office_escape_db;

-- 2. Select Database
USE office_escape_db;

-- 3. Create Leaderboard Table
CREATE TABLE IF NOT EXISTS leaderboard (
   id BIGINT AUTO_INCREMENT PRIMARY KEY,
   player_name VARCHAR(255),
   score INT,
   created_at DATETIME
);
