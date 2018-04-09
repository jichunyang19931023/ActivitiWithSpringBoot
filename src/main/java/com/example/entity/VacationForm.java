package com.example.entity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;

@Entity
@Table(name = "vacation_form")
public class VacationForm {
    @Id
    @GeneratedValue
    private Integer id;
	
    private String title;
    
    private String content;
    
    private String applicant;
    
    private String approver;

    @Transient
    private String state;
    
	public VacationForm(){

    }
    public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
	
	public String getApplicant() {
		return applicant;
	}
	
	public void setApplicant(String applicant) {
		this.applicant = applicant;
	}
	
	public String getApprover() {
		return approver;
	}
	public void setApprover(String approver) {
		this.approver = approver;
	}
	
	public String getState() {
		return state;
	}
	public void setState(String state) {
		this.state = state;
	}
}