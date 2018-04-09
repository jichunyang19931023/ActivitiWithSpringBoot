package com.example.service;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.entity.VacationForm;

public interface VacationFormService extends JpaRepository<VacationForm, Integer>{
	
}