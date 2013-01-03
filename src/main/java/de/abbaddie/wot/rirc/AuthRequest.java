package de.abbaddie.wot.rirc;

import java.io.Serializable;

public class AuthRequest implements Serializable {
	private static final long serialVersionUID = 1L;
	
	public String name;
	
	public AuthRequest(String name) {
		this.name = name;
	}
}