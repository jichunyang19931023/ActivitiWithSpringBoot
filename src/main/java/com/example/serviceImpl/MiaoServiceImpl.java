package com.example.serviceImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

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
import org.activiti.engine.FormService;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.form.TaskFormData;
import org.activiti.engine.history.HistoricDetail;
import org.activiti.engine.history.HistoricFormProperty;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;

import com.example.entity.DefaultModel;
import com.example.entity.ModelData;
import com.example.entity.User;
import com.example.service.BpmnModelService;
import com.example.service.DefaultModelService;
import com.example.service.MiaoService;
import com.example.service.UserService;
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
	private HistoryService historyService;

	@Autowired
	private FormService formService;

	@Autowired
	private RepositoryService repositoryService;

	@Autowired
	private UserService userService;

	@Autowired
	private BpmnModelService modelService;

	@Autowired
	private DefaultModelService defaultModelService;

	@Override
	public void completeProcess(String processId, HashMap<String, String> properties, String operator, String input) {
		Task task = taskService.createTaskQuery().processInstanceId(processId).singleResult();

		if (properties.size() > 0) {
			properties.put("id", processId);
			properties.put("writer", operator);
			properties.put("create_time", new Date().toString());

			formService.saveFormData(task.getId(), properties);
		}
		// 如果是流转节点
		if (!"".equals(input) && input != null) {
			// 根据businessKey找到当前任务节点
			// 设置输入参数，使流程自动流转到对应节点
			taskService.setVariable(task.getId(), "input", input);
			taskService.complete(task.getId());
		}

		// 如果是审批节点
		if (isApprover(operator)) {
			HashMap<String, String> params = new HashMap<String, String>();
			params.put("approver", operator);
			params.put("approve_time", new Date().toString());

			formService.saveFormData(task.getId(), params);
		}

		Task nextTask = taskService.createTaskQuery().processInstanceId(processId).singleResult();
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
	public List<HashMap<String, Object>> formList() {
		List<HistoricProcessInstance> processInstances = historyService.createHistoricProcessInstanceQuery()
				.orderByProcessInstanceStartTime().asc().list();
		List<HashMap<String, Object>> formList = new ArrayList<HashMap<String, Object>>();

		for (HistoricProcessInstance processInstance : processInstances) {
			Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
			HashMap<String, Object> form = getFormData(processInstance.getId());
			form.put("processId", processInstance.getId());
			if (task != null) {
				String state = task.getName();
				form.put("state", state);
			} else {
				form.put("state", "已结束");
			}
			formList.add(form);
		}
		return formList;
	}

	// 获取表单信息
	private HashMap<String, Object> getFormData(String processId) {
		List<HistoricDetail> formProperties = historyService.createHistoricDetailQuery().processInstanceId(processId)
				.formProperties().list();
		HashMap<String, Object> form = new HashMap<String, Object>();

		for (HistoricDetail historicDetail : formProperties) {
			HistoricFormProperty field = (HistoricFormProperty) historicDetail;
			form.put(field.getPropertyId(), field.getPropertyValue());
		}
		return form;
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
	public List<HashMap<String, String>> historyState(String processId) {
		List<HashMap<String, String>> processList = new ArrayList<HashMap<String, String>>();
		// 先按开始时间排序，如果开始时间一致，需要按照id排序
		List<HistoricTaskInstance> list = historyService.createHistoricTaskInstanceQuery().processInstanceId(processId)
				.orderByHistoricTaskInstanceStartTime().asc().orderByTaskId().asc().orderByHistoricTaskInstanceEndTime()
				.asc().list();
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
	public List<HashMap<String, String>> getButtons(String processId, Integer type) {
		Task task = taskService.createTaskQuery().processInstanceId(processId).singleResult();
		List<HashMap<String, String>> buttons = new ArrayList<HashMap<String, String>>();
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
	public List<HashMap<String, String>> getNextNodes(Task task, String userType) {
		List<HashMap<String, String>> buttons = new ArrayList<HashMap<String, String>>();
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
					boolean isMultiSub = sub.hasMultiInstanceLoopCharacteristics();
					if (isMultiSub) {
						List<User> users = userService.findAll();
						users = users.stream().filter(item -> item.getType() == 1).collect(Collectors.toList());
						taskService.setVariable(task.getId(), "applyList", users);
					}
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
					if (userType.equals(nextUserTask.getDocumentation())) {
						// 获取出线的条件
						String input = sequenceFlow.getConditionExpression();
						if (input != null && !"".equals(input)) {
							operation = getInputValue(input);
						} else if (sequenceFlow.getSourceFlowElement() instanceof StartEvent) {
							operation = getInputValue(subCondition);
						}
						// 返回按钮名称和对应的触发条件的值
						HashMap<String, String> map = new HashMap<String, String>();
						map.put("name", nextUserTask.getName());
						map.put("operation", operation);
						buttons.add(map);
					}
				}
			}
		} else if (userType.equals(myTask.getDocumentation())) {
			// 如果是单条线，则没有条件值，operation默认为null
			HashMap<String, String> map = new HashMap<String, String>();
			map.put("name", myTask.getName());
			map.put("operation", operation);
			buttons.add(map);
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
			// 保存默认模板
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

	// 刷新当前流程
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

	@Override
	public List<HashMap<String, String>> getFormNames() {
		List<HashMap<String, String>> list = new ArrayList<HashMap<String, String>>();
		// 获取默认流程
		DefaultModel defaultModel = defaultModelService.findAll().get(0);
		ModelData modelData = modelService.findOne(defaultModel.getModel_id());
		String instanceKey = modelData.getModel_key();

		// 开始流程
		ProcessInstance process = runtimeService.startProcessInstanceByKey(instanceKey);

		Task task = taskService.createTaskQuery().processInstanceId(process.getId()).singleResult();
		TaskFormData formData = formService.getTaskFormData(task.getId());

		List<FormProperty> formProperties = formData.getFormProperties();
		for (FormProperty formProperty : formProperties) {
			String formId = formProperty.getId();
			String formName = formProperty.getName();
			if (formProperty.isRequired()) {
				HashMap<String, String> map = new HashMap<String, String>();
				map.put("id", formId);
				map.put("name", formName);
				map.put("processId", process.getId());
				list.add(map);
			}
		}
		return list;
	}

	public HashMap<String, String> getFormFormat() {
		HashMap<String, String> formMap = new HashMap<String, String>();
		DefaultModel defaultModel = defaultModelService.findAll().get(0);
		ModelData modelData = modelService.findOne(defaultModel.getModel_id());
		String instanceKey = modelData.getModel_key();
		ProcessDefinition pd = repositoryService.createProcessDefinitionQuery().processDefinitionKey(instanceKey)
				.latestVersion().singleResult();
		BpmnModel bpmnModel = repositoryService.getBpmnModel(pd.getId());
		Process process = bpmnModel.getProcesses().get(0);
		// 获取所有的FlowElement信息
		Collection<FlowElement> flowElements = process.getFlowElements();
		for (FlowElement flowElement : flowElements) {
			// 如果是任务节点
			if (flowElement instanceof UserTask) {
				UserTask userTask = (UserTask) flowElement;
				List<org.activiti.bpmn.model.FormProperty> forms = userTask.getFormProperties();
				for (org.activiti.bpmn.model.FormProperty form : forms) {
					if (form.isReadable()) {
						formMap.put(form.getId(), form.getName());
					}
				}
			}
		}
		return formMap;
	}

	public DefaultModel getDefaultModel(List<ModelData> models, HttpServletRequest request) {
		DefaultModel de = null;
		// 获取默认的流程文件
		List<DefaultModel> defaults = defaultModelService.findAll();
		// 如果没有默认流程文件，则将最新的流程文件设置为默认
		if (defaults.size() == 0) {
			DefaultModel d = new DefaultModel();
			d.setLast_updated(new Date());
			d.setModel_id(models.get(0).getId());
			d.setModel_name(models.get(0).getName());
			d.setUpdate_person("system");
			d.setIp(CommonUtil.getIpAddr(request));
			defaultModelService.save(d);
			de = d;
		} else {
			de = defaults.get(0);
			ModelData m = modelService.findOne(de.getModel_id());
			if (m != null) {
				de.setModel_name(m.getName());
			}
		}
		return de;
	}

	@Override
	public HashMap<String, Object> getFormData(List<HashMap<String, Object>> forms, HttpServletRequest request, List<HashMap<String, Object>> formButtons, List<List<HashMap<String, Object>>> allList) {
		HashMap<String, Object> mapdata = new HashMap<String, Object>();
		// 从cookie中获取当前用户
		String user = "";
		user = getCurrentUser(request);
		List<User> users = userService.findByName(user);
		Integer uType = users.get(0).getType();

		String finalUser = user;
		//撰写者只能看到自己的数据
		if (uType == 1) {
			forms = forms.stream().filter(form -> finalUser.equals(form.get("writer").toString()))
					.collect(Collectors.toList());
		} else {
			forms = forms.stream().filter(form -> form.get("state").toString().contains("审核"))
					.collect(Collectors.toList());
		}

		List<ModelData> models = new ArrayList<ModelData>();
		DefaultModel de = new DefaultModel();
		
		Sort sort = new Sort(Direction.DESC, "updateTime");
		models = modelService.findAll(sort);
		
		if (models != null) {
			// 查找所有的流程文件（按照更新时间降序排序）
			models = models.stream().filter(item -> item.getModel_type().equals(0)).collect(Collectors.toList());
			if (models.size() > 0) {
				de = getDefaultModel(models, request);
			}
		}
		//刷新当前流程文件
		refreshBpmn(de.getModel_id());

		List<HashMap<String, String>> buttons = new ArrayList<HashMap<String, String>>();
		List<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();
		//获取所有的form标题栏并加入列表
		HashMap<String, String> formFormat = getFormFormat();
		for (String key : formFormat.keySet()) {
			HashMap<String, Object> map = new HashMap<String, Object>();
			if (!"".equals(key)) {
				map.put("name", key);
				map.put("value", formFormat.get(key) != null ? formFormat.get(key) : "");
				list.add(map);
			}
		}
		HashMap<String, Object> map1 = new HashMap<String, Object>();
		map1.put("name", "state");
		map1.put("value", "状态");
		list.add(map1);
		allList.add(list);

		//获取所有的数据并加入列表
		for (HashMap<String, Object> form : forms) {
			//当前用户下一步需要显示的按钮
			buttons = getButtons(form.get("id").toString(), uType);
			String approver = "";
			if (form.get("approver") != null) {
				approver = form.get("approver").toString();
			}
			// 同一用户不可重复审核
			if (!user.equals(approver)) {
				List<HashMap<String, Object>> mylist = new ArrayList<HashMap<String, Object>>();
				//循环所有的列表标题
				for (HashMap<String, Object> j : list) {
					//将标题对应的数据加入列表
					if (j.get("name") != null) {
						HashMap<String, Object> map = new HashMap<String, Object>();
						map.put("name", j.get("name"));
						map.put("value", form.get(j.get("name")) != null ? form.get(j.get("name")) : "");
						mylist.add(map);
					}
				}
				allList.add(mylist);

				HashMap<String, Object> bus = new HashMap<String, Object>();
				bus.put("formId", form.get("id"));
				bus.put("processId", form.get("processId"));
				bus.put("buttons", buttons);
				formButtons.add(bus);
			}
		}
		mapdata.put("de", de);
		mapdata.put("models", models);
		return mapdata;
	}
}
