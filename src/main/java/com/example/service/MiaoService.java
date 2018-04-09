package com.example.service;

import java.util.HashMap;
import java.util.List;

import com.example.entity.User;
import com.example.entity.VacationForm;

public interface MiaoService {

	public VacationForm writeForm(String title, String content, String applicant);
	
	public boolean giveupVacation(String formId, String operator);
	
	public boolean applyVacation(String formId, String operator);
	
	public boolean approverVacation(String formId, String operator);
	
	public void completeProcess(String formId, String operator, String input);
	
	public HashMap<String,String> getCurrentState(String formId);

	public List<VacationForm> formList();

	public User loginSuccess(String user);

	public List historyState(String formId);
}
