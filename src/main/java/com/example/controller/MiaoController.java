package com.example.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.entity.DefaultModel;
import com.example.entity.ModelData;
import com.example.entity.User;
import com.example.service.MiaoService;
import com.example.service.UserService;

@Controller
public class MiaoController {
	@Autowired
	private MiaoService miaoService;

	@Autowired
	private UserService userService;

	@GetMapping("/")
	public String login() {
		return "login";
	}

	// 首页
	@GetMapping("/home")
	public String index(ModelMap model, HttpServletRequest request) {
		List<ModelData> models = new ArrayList<ModelData>();
		Integer modelCount = 0;
		DefaultModel de = new DefaultModel();
		List<HashMap<String, Object>> forms = miaoService.formList();
		List<HashMap<String,Object>> formButtons = new ArrayList<HashMap<String,Object>>();
		List<List<HashMap<String,Object>>> allList = new ArrayList<List<HashMap<String,Object>>>();
		HashMap<String, Object> mapData = miaoService.getFormData(forms, request, formButtons, allList);
		de = (DefaultModel) mapData.get("de");
		models = (List<ModelData>) mapData.get("models");
		String user = "";
		user = miaoService.getCurrentUser(request);
		List<User> users = userService.findByName(user);
		Integer uType = users.get(0).getType();
		
		modelCount = models != null?models.size():modelCount;
		
		//只有admin才能看到流程模板列表
		if (miaoService.isAdmin(user)) {
			model.addAttribute("models", models);
		}

		model.addAttribute("formButtons",formButtons);
		//当前默认流程模板
		model.addAttribute("defaultModel", de);
		// 将forms参数返回
		model.addAttribute("forms", allList);
		model.addAttribute("userType", uType);
		model.addAttribute("modelCount", modelCount);
		return "index";
	}

	// 请假单页面
	@GetMapping("/form")
	public String getFormNames(ModelMap model) {
		List<HashMap<String, String>> formNames = miaoService.getFormNames();
		model.addAttribute("formNames", formNames);
		model.addAttribute("processId", formNames.get(0).get("processId"));
		return "form";
	}
}
