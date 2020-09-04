package com.example.serviceImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.Gateway;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.bpmn.model.StartEvent;
import org.activiti.bpmn.model.SubProcess;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.entity.DefaultModel;
import com.example.entity.ModelData;
import com.example.entity.User;
import com.example.entity.VacationForm;
import com.example.service.BpmnModelService;
import com.example.service.DefaultModelService;
import com.example.service.MiaoService;
import com.example.service.UserService;
import com.example.service.VacationFormService;
import com.example.util.CommonUtil;
import com.example.util.editor.language.json.converter.BpmnJsonConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service("miaoService")
public class MiaoServiceImpl implements MiaoService {
	@Autowired
	private RuntimeService runtimeService;

	@Autowired
	private TaskService taskService;

	@Autowired
	HistoryService historyService;

	@Autowired
	RepositoryService repositoryService;

	@Autowired
	private VacationFormService vacationFormService;

	@Autowired
	private UserService userService;

	@Autowired
	private BpmnModelService modelService;
	
	@Autowired
	private DefaultModelService defaultModelService;
	// 填写请假信息
	@Override
	public VacationForm writeForm(String title, String content, String applicant) {
		VacationForm form = new VacationForm();
		String approver = "未知审批者";
		form.setTitle(title);
		form.setContent(content);
		form.setApplicant(applicant);
		form.setApprover(approver);
		vacationFormService.save(form);

		// 开始请假流程，使用formId作为流程的businessKey
		runtimeService.startProcessInstanceByKey("myProcess", form.getId().toString());
		Task task = taskService.createTaskQuery().processInstanceBusinessKey(form.getId().toString()).singleResult();
		taskService.claim(task.getId(), applicant);
		return form;
	}

	// 根据选择，申请或放弃请假
	@Override
	public void completeProcess(String formId, String operator, String input) {
		// 如果是流转节点
		if (!"".equals(input) && input != null) {
			// 根据businessKey找到当前任务节点
			Task task = taskService.createTaskQuery().processInstanceBusinessKey(formId).singleResult();
			// 设置输入参数，使流程自动流转到对应节点
			taskService.setVariable(task.getId(), "input", input);
			taskService.complete(task.getId());
		}
		// 如果是审批节点
		if (isApprover(operator)) {
			VacationForm form = vacationFormService.findOne(Integer.parseInt(formId));
			if (form != null) {
				form.setApprover(operator);
				vacationFormService.save(form);
			}
		}

		Task nextTask = taskService.createTaskQuery().processInstanceBusinessKey(formId).singleResult();
		// 认领任务
		taskService.claim(nextTask.getId(), operator);

		UserTask myTask = getUserTask(nextTask);
		// 如果该任务的出口连线只有一条，才完成当前任务
		List<SequenceFlow> flows = myTask.getOutgoingFlows();
		if (flows.size() == 1) {
			// 完成任务
			taskService.complete(nextTask.getId());
		}
	}

	// 获取请假信息的当前流程状态
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

	// 请假列表
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

	// 登录验证用户名是否存在
	@Override
	public User loginSuccess(String username) {
		List<User> users = userService.findByName(username);
		if (users != null && users.size() > 0) {
			return users.get(0);
		}
		return null;
	}

	// 获取当前登录用户
	@Override
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

	// 获取已执行的流程信息
	@Override
	public List<HashMap<String, String>> historyState(String formId) {
		List<HashMap<String, String>> processList = new ArrayList<HashMap<String, String>>();
		// 先按开始时间排序，如果开始时间一致，需要按照id排序
		List<HistoricTaskInstance> list = historyService.createHistoricTaskInstanceQuery()
				.processInstanceBusinessKey(formId).orderByHistoricTaskInstanceStartTime().asc().orderByTaskId().asc()
				.orderByHistoricTaskInstanceEndTime().asc().list();
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

	// 判断是否是审批人
	@Override
	public boolean isApprover(String username) {
		List<User> users = userService.findByName(username);
		User user = users.get(0);
		if (user.getType() == 2) {
			return true;
		}
		return false;
	}

	// 判断是否是管理员
	@Override
	public boolean isAdmin(String username) {
		List<User> users = userService.findByName(username);
		User user = users.get(0);
		if (user.getType() == 0) {
			return true;
		}
		return false;
	}

	@Override
	// 获取当前状态下应该显示的按钮
	public HashMap<String, String> getButtons(Integer formId, Integer type) {
		Task task = taskService.createTaskQuery().processInstanceBusinessKey(formId.toString()).singleResult();
		HashMap<String, String> buttons = new HashMap<String, String>();
		String userType = "apply";
		if (type == 2) {
			userType = "manager";
		}
		if (task != null) {
			buttons = getNextNodes(task, userType);
		}
		return buttons;
	}

	// 获取下一个节点
	public HashMap<String, String> getNextNodes(Task task, String userType) {
		HashMap<String, String> buttons = new HashMap<String, String>();
		UserTask myTask = getUserTask(task);

		// 获取当前任务的出线信息
		List<SequenceFlow> outFlows = myTask.getOutgoingFlows();
		List<SequenceFlow> newOutFlows = new ArrayList<SequenceFlow>();
		String subCondition = null;
		for (SequenceFlow sequenceFlow : outFlows) {
			// 出线连接的节点对象
			FlowElement taskElement = sequenceFlow.getTargetFlowElement();
			// 如果节点对象为网关类型
			if (taskElement instanceof Gateway) {
				Gateway gateway = (Gateway) taskElement;
				// 获取网关的出线信息
				newOutFlows.addAll(gateway.getOutgoingFlows());
			} else if (taskElement instanceof SubProcess) {
				SubProcess sub = (SubProcess) taskElement;
				if (sub != null) {
					// 获取所有的FlowElement信息
					Collection<FlowElement> subflowElements = sub.getFlowElements();
					for (FlowElement flowElement : subflowElements) {
						// 如果是开始节点
						if (flowElement instanceof StartEvent) {
							StartEvent start = (StartEvent) flowElement;
							subCondition = sequenceFlow.getConditionExpression();
							newOutFlows.addAll(start.getOutgoingFlows());
							break;
						}
					}
				}
			} else {
				newOutFlows.add(sequenceFlow);
			}
		}
		String operation = null;

		// 如果出线条数大于1，需要显示多个按钮
		if (newOutFlows.size() > 1) {
			for (SequenceFlow sequenceFlow : newOutFlows) {
				// 获取出线所连接的任务节点
				FlowElement taskElement = sequenceFlow.getTargetFlowElement();

				if (taskElement instanceof UserTask) {
					UserTask nextUserTask = (UserTask) taskElement;
					// 判断当前用户是否拥有操作该任务节点的权限
					if (userType.equals(nextUserTask.getCategory())) {
						// 获取出线的条件
						String input = sequenceFlow.getConditionExpression();
						if (input != null && !"".equals(input)) {
							operation = getInputValue(input);
						} else if (sequenceFlow.getSourceFlowElement() instanceof StartEvent) {
							operation = getInputValue(subCondition);
						}
						// 返回按钮名称和对应的触发条件的值
						buttons.put(nextUserTask.getName(), operation);
					}
				}
			}
		} else if (userType.equals(myTask.getCategory())) {
			// 如果是单条线，则没有条件值，operation默认为null
			buttons.put(myTask.getName(), operation);
		}
		return buttons;
	}

	private String getInputValue(String input) {
		String[] inputArray = input.split("==");
		String operation = inputArray[inputArray.length - 1];
		// 获取条件的值，比如条件为${input=='save'},则获取的为save
		if (operation.contains("'") || operation.contains("\"")) {
			operation = operation.substring(1, operation.length() - 2);
		}
		return operation;
	}

	public UserTask getUserTask(Task task) {
		String processDefinitionId = task.getProcessDefinitionId();
		BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
		// 因为我们这里只定义了一个Process 所以获取集合中的第一个即可
		Process process = bpmnModel.getProcesses().get(0);
		// 获取所有的FlowElement信息
		Collection<FlowElement> flowElements = process.getFlowElements();
		UserTask myTask = new UserTask();
		SubProcess sub = null;
		for (FlowElement flowElement : flowElements) {
			// 如果是任务节点
			if (flowElement instanceof UserTask) {
				UserTask userTask = (UserTask) flowElement;
				if (userTask.getName().equals(task.getName())) {
					myTask = userTask;
					break;
				}
			} else if (flowElement instanceof SubProcess) {
				sub = (SubProcess) flowElement;
				// 如果存在子流程，需要在子流程中查询任务节点
				if (sub != null) {
					// 获取所有的FlowElement信息
					Collection<FlowElement> subflowElements = sub.getFlowElements();
					for (FlowElement element : subflowElements) {
						// 如果是任务节点
						if (element instanceof UserTask) {
							UserTask userTask = (UserTask) element;
							if (userTask.getName().equals(task.getName())) {
								myTask = userTask;
								break;
							}
						}
					}
				}
			}
		}
		return myTask;
	}
	
	public String getJsonBpmn(String modelId, HttpServletRequest request) {
		try {
			String filename = refreshBpmn(modelId);
			
			DefaultModel defaultModel = defaultModelService.findAll().get(0);
			defaultModel.setModel_id(modelId);
			defaultModel.setUpdate_person(getCurrentUser(request));
			defaultModel.setLast_updated(new Date());
			defaultModel.setIp(CommonUtil.getIpAddr(request));
			defaultModelService.save(defaultModel);
			
			return filename;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public String refreshBpmn(String modelId) {
		ModelData modelData = modelService.findOne(modelId);
		if (modelData == null) {
			return null;
		}
		String jsonData = modelData.getModel_editor_json();
		byte[] bytes = jsonData.getBytes();

		ObjectMapper mapper = new ObjectMapper();
		try {
			JsonNode root = mapper.readTree(bytes);
			BpmnJsonConverter bpmn = new BpmnJsonConverter();
			BpmnModel model = bpmn.convertToBpmnModel(root);
			String filename = model.getMainProcess().getId() + ".bpmn20.xml";
			byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(model);
			String processName = modelData.getName() + ".bpmn20.xml";
			repositoryService.createDeployment().name(modelData.getName())
					.addString(processName, new String(bpmnBytes, "utf-8")).deploy();		
			return filename;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
