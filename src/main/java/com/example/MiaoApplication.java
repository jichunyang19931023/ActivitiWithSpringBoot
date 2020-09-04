package com.example;

import java.util.HashMap;
import java.util.List;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.entity.User;
import com.example.entity.VacationForm;
import com.example.service.MiaoService;
import com.example.util.ResultInfo;

@RestController
@SpringBootApplication
public class MiaoApplication {
	@Autowired
	private MiaoService miaoService;
	
	public static void main(String[] args) {
		SpringApplication.run(MiaoApplication.class, args);
	}
	
	@PostMapping( "/login")
	public ResultInfo login(HttpServletRequest request, HttpServletResponse response){
		ResultInfo result = new ResultInfo();
		String username = request.getParameter("username");
		User user = miaoService.loginSuccess(username);
		if(user != null) {
			result.setCode(200);
			result.setMsg("登录成功");
			result.setInfo(user);
			//用户信息存放在Cookie中，实际情况下保存在Redis更佳
			Cookie cookie = new Cookie("userInfo", username);
			cookie.setPath("/");
			response.addCookie(cookie);
		}else {
			result.setCode(300);
			result.setMsg("登录名不存在，登录失败");
		}
		return result;
	}
	
	@GetMapping("/logout")
	public ResultInfo logout(HttpServletRequest request, HttpServletResponse response) {
		ResultInfo result = new ResultInfo();
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if (cookie.getName().equals("userInfo")) {
					cookie.setValue(null);
					// 立即销毁cookie
					cookie.setMaxAge(0);
					cookie.setPath("/");
					response.addCookie(cookie);
					break;
				}
			}
		}
		result.setCode(200);
		return result;
	}
	
	//添加请假单
	@GetMapping( "/writeForm")
	public ResultInfo writeForm(HttpServletRequest request){
		ResultInfo result = new ResultInfo();
		String title = request.getParameter("title");
		String content = request.getParameter("content");
		String operator = request.getParameter("operator");
		VacationForm form = miaoService.writeForm(title,content,operator);
		result.setCode(200);
		result.setMsg("填写请假条成功");
		result.setInfo(form);
		return result;
	}
	
	//下一步操作
	@GetMapping( "/next")
	public ResultInfo giveup(HttpServletRequest request){
		ResultInfo result = new ResultInfo();
		String formId = request.getParameter("formId");
		String operator = request.getParameter("operator");
		String input = request.getParameter("input");
		miaoService.completeProcess(formId, operator, input);
		result.setCode(200);
		return result;
	}
	
	//获取某条请假信息当前状态
	@GetMapping( "/currentState")
	public HashMap<String,String> currentState(HttpServletRequest request){
		String formId = request.getParameter("formId");
		HashMap<String,String> map = new HashMap<String,String>();
		map = miaoService.getCurrentState(formId);
		return map;
	}
	
	@GetMapping( "/historyState")
	public ResultInfo queryHistoricTask(HttpServletRequest request){
		ResultInfo result = new ResultInfo();
		String formId = request.getParameter("formId");
		List process = miaoService.historyState(formId);
		result.setCode(200);
		result.setInfo(process);
		return result;
    }
	
	@GetMapping( "/changeWorkFlow")
	public ResultInfo changeWorkFlow(HttpServletRequest request){
		String modelId = request.getParameter("modelId");
		ResultInfo result = new ResultInfo();
		String fileName = miaoService.getJsonBpmn(modelId, request);
		result.setCode(200);
		result.setInfo(fileName);
		return result;
    }
	
	
	@GetMapping( "/refreshBpmn")
	public ResultInfo refreshBpmn(HttpServletRequest request){
		String modelId = request.getParameter("modelId");
		ResultInfo result = new ResultInfo();
		String fileName = miaoService.refreshBpmn(modelId);
		result.setCode(200);
		result.setInfo(fileName);
		return result;
    }
}
