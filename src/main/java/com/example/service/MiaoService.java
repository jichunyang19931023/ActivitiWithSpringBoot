package com.example.service;

import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.example.entity.DefaultModel;
import com.example.entity.ModelData;
import com.example.entity.User;

public interface MiaoService {

	public void completeProcess(String processId, HashMap<String, String> properties, String operator, String input);
	
	public HashMap<String,String> getCurrentState(String formId);

	public List<HashMap<String, Object>> formList();

	public User loginSuccess(String user);

	public List<HashMap<String, String>> historyState(String formId);
	
	public boolean isApprover(String username);

	public String getCurrentUser(HttpServletRequest request);

	public String getJsonBpmn(String modelId, HttpServletRequest request);

	public String refreshBpmn(String modelId);
	
	boolean isAdmin(String username);

	public List<HashMap<String, String>> getFormNames();

	public List<HashMap<String, String>> getButtons(String processId, Integer type);
	
	public HashMap<String, String> getFormFormat();

	public DefaultModel getDefaultModel(List<ModelData> models, HttpServletRequest request);

	public HashMap<String, Object> getFormData(List<HashMap<String, Object>> forms, HttpServletRequest request,
			List<HashMap<String, Object>> formButtons, List<List<HashMap<String, Object>>> allList);

}
