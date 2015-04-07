package com.hortonworks.amstore.view;

public abstract class Endpoint {
	String url = "";
	String username = "";
	String password = "";

	public Endpoint(String url, String username,
			String password) {

		this.url = url;
		this.username = username;
		this.password = password;
	}

	public abstract boolean isAvailable();

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}



}
