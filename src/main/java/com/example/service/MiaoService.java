package com.example.service;

import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.example.entity.User;
import com.example.entity.VacationForm;

public interface MiaoService {

	public VacationForm writeForm(String title, String content, String applicant);
	
	public void completeProcess(String formId, String operator, String input);
	
	public HashMap<String,String> getCurrentState(String formId);

	public List<VacationForm> formList();

	public User loginSuccess(String user);

	public List<HashMap<String, String>> historyState(String formId);
	
	public boolean isApprover(String username);

	public HashMap<String, String> getButtons(Integer id, Integer type);
	
	public String getCurrentUser(HttpServletRequest request);

	public String getJsonBpmn(String modelId, HttpServletRequest request);

	public String refreshBpmn(String modelId);
	
	boolean isAdmin(String username);
}
