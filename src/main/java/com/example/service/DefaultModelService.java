package com.example.service;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.entity.DefaultModel;

public interface DefaultModelService extends JpaRepository<DefaultModel, Long>{
	
}