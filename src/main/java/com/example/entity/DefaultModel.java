package com.example.entity;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.mysql.jdbc.Blob;

//默认模型表
@Entity
@Table(name = "DEFAULT_MODEL")
public class DefaultModel {
    @Id
    @GeneratedValue
    private Long id;
	
	private String model_id;
    
    private Date last_updated;
    
    private String ip;
    
    private String update_person;
    
    @Transient
    private String model_name;

	public String getModel_name() {
		return model_name;
	}

	public void setModel_name(String model_name) {
		this.model_name = model_name;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getModel_id() {
		return model_id;
	}

	public void setModel_id(String model_id) {
		this.model_id = model_id;
	}

	public Date getLast_updated() {
		return last_updated;
	}

	public void setLast_updated(Date last_updated) {
		this.last_updated = last_updated;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getUpdate_person() {
		return update_person;
	}

	public void setUpdate_person(String update_person) {
		this.update_person = update_person;
	}
    
	
}