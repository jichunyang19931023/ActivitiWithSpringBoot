package com.example.service;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.entity.User;

public interface UserService extends JpaRepository<User, Long>{
	public List<User> findByName(String name);
}