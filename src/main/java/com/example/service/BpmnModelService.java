package com.example.service;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.entity.ModelData;

public interface BpmnModelService extends JpaRepository<ModelData, String>{
	
}