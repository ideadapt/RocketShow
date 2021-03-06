package com.ascargon.rocketshow;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Session {

	private String currentSetName;
	private boolean firstStart = true;
	private boolean updateFinished = false;
	
	public Session() {
	}
	
	@XmlElement
	public String getCurrentSetName() {
		return currentSetName;
	}

	public void setCurrentSetName(String currentSetName) {
		this.currentSetName = currentSetName;
	}

	@XmlElement
	public boolean isFirstStart() {
		return firstStart;
	}

	public void setFirstStart(boolean firstStart) {
		this.firstStart = firstStart;
	}

	@XmlElement
	public boolean isUpdateFinished() {
		return updateFinished;
	}

	public void setUpdateFinished(boolean updateFinished) {
		this.updateFinished = updateFinished;
	}
	
}
