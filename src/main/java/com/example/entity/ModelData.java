package com.example.entity;

import java.util.Date;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

//bpmn信息表
@Entity
@Table(name = "ACT_DE_MODEL")
public class ModelData {
    @Id
    private String id;
	
	private String name;

	private String model_key;
    
    private String description;
    
    private String model_comment;
    
    private String model_editor_json;
    
    @Column(name="last_updated")
    private Date updateTime;
    
    private Integer model_type;
    
	public String getModel_key() {
		return model_key;
	}

	public void setModel_key(String model_key) {
		this.model_key = model_key;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getModel_comment() {
		return model_comment;
	}

	public void setModel_comment(String model_comment) {
		this.model_comment = model_comment;
	}

	public String getModel_editor_json() {
		return model_editor_json;
	}

	public void setModel_editor_json(String model_editor_json) {
		this.model_editor_json = model_editor_json;
	}
    
    public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}


	public Integer getModel_type() {
		return model_type;
	}

	public void setModel_type(Integer model_type) {
		this.model_type = model_type;
	}

	public Date getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(Date updateTime) {
		this.updateTime = updateTime;
	}

}