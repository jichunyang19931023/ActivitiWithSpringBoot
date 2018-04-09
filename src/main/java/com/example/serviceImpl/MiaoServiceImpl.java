package com.example.serviceImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.activiti.engine.HistoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.entity.User;
import com.example.entity.VacationForm;
import com.example.service.MiaoService;
import com.example.service.UserService;
import com.example.service.VacationFormService;

@Service("miaoService")
public class MiaoServiceImpl implements MiaoService {
	@Autowired
	private RuntimeService runtimeService;

	@Autowired
	private TaskService taskService;

	@Autowired
	HistoryService historyService;

	@Autowired
	private VacationFormService vacationFormService;

	@Autowired
	private UserService userService;

	//填写请假信息
	@Override
	public VacationForm writeForm(String title, String content, String applicant) {
		VacationForm form = new VacationForm();
		String approver = "未知审批者";
		form.setTitle(title);
		form.setContent(content);
		form.setApplicant(applicant);
		form.setApprover(approver);
		vacationFormService.save(form);

		Map<String, Object> variables = new HashMap<String, Object>();
		variables.put("employee", form.getApplicant());
		//开始请假流程，使用formId作为流程的businessKey
		runtimeService.startProcessInstanceByKey("myProcess", form.getId().toString(), variables);
		return form;
	}

	//根据选择，申请或放弃请假
	@Override
	public void completeProcess(String formId, String operator, String input) {
		//根据businessKey找到当前任务节点
		Task task = taskService.createTaskQuery().processInstanceBusinessKey(formId).singleResult();
		//设置输入参数，使流程自动流转到对应节点
		taskService.setVariable(task.getId(), "input", input);
		taskService.complete(task.getId());
		if ("apply".equals(input)) {
			applyVacation(formId, operator);
		} else {
			giveupVacation(formId, operator);
		}
	}

	//放弃请假
	@Override
	public boolean giveupVacation(String formId, String operator) {
		Task task = taskService.createTaskQuery().processInstanceBusinessKey(formId).singleResult();
		Map<String, Object> variables = new HashMap<String, Object>();
		variables.put("employee", operator);
		//认领任务
		taskService.claim(task.getId(), operator);
		//完成任务
		taskService.complete(task.getId(), variables);
		return true;
	}

	@Override
	public boolean applyVacation(String formId, String operator) {
		Task task = taskService.createTaskQuery().processInstanceBusinessKey(formId).singleResult();
		Map<String, Object> variables = new HashMap<String, Object>();
		List<User> users = userService.findAll();
		String managers = "";
		//获取所有具有审核权限的用户
		for (User user : users) {
			if (user.getType().equals(2)) {
				managers += user.getName() + ",";
			}
		}
		managers = managers.substring(0, managers.length() - 1);
		variables.put("employee", operator);
		variables.put("managers", managers);
		taskService.claim(task.getId(), operator);
		taskService.complete(task.getId(), variables);
		return true;
	}

	@Override
	public boolean approverVacation(String formId, String operator) {
		Task task = taskService.createTaskQuery().processInstanceBusinessKey(formId).singleResult();
		taskService.claim(task.getId(), operator);
		taskService.complete(task.getId());
		//更新请假信息的审核人
		VacationForm form = vacationFormService.findOne(Integer.parseInt(formId));
		if (form != null) {
			form.setApprover(operator);
			vacationFormService.save(form);
		}
		return true;
	}

	//获取请假信息的当前流程状态
	@Override
	public HashMap<String, String> getCurrentState(String formId) {
		HashMap<String, String> map = new HashMap<String, String>();
		Task task = taskService.createTaskQuery().processInstanceBusinessKey(formId).singleResult();
		if (task != null) {
			map.put("status", "processing");
			map.put("taskId", task.getId());
			map.put("taskName", task.getName());
			map.put("user", task.getAssignee());
		} else {
			map.put("status", "finish");
		}
		return map;
	}

	//请假列表
	@Override
	public List<VacationForm> formList() {
		List<VacationForm> formList = vacationFormService.findAll();
		for (VacationForm form : formList) {
			Task task = taskService.createTaskQuery().processInstanceBusinessKey(form.getId().toString())
					.singleResult();
			if (task != null) {
				String state = task.getName();
				form.setState(state);
			} else {
				form.setState("已结束");
			}
		}
		return formList;
	}

	//登录验证用户名是否存在
	@Override
	public User loginSuccess(String username) {
		List<User> users = userService.findByName(username);
		if (users != null && users.size() > 0) {
			User user = users.get(0);
			return user;
		}
		return null;
	}

	//获取当前登录用户
	public String getCurrentUser(HttpServletRequest request) {
		String user = "";
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if (cookie.getName().equals("userInfo")) {
					user = cookie.getValue();
				}
			}
		}
		return user;
	}

	//获取已执行的流程信息
	@Override
	public List historyState(String formId) {
		List<HashMap<String, String>> processList = new ArrayList<HashMap<String, String>>();
		List<HistoricTaskInstance> list = historyService.createHistoricTaskInstanceQuery()
				.processInstanceBusinessKey(formId).list();
		if (list != null && list.size() > 0) {
			for (HistoricTaskInstance hti : list) {
				HashMap<String, String> map = new HashMap<String, String>();
				map.put("name", hti.getName());
				map.put("operator", hti.getAssignee());
				processList.add(map);
			}
		}
		return processList;
	}
}
