package de.abbaddie.wot.rirc;

import java.io.Serializable;

public class AuthResponse implements Serializable {
	private static final long serialVersionUID = 1L;
	
	public boolean found;
	public String hash;
	
	public AuthResponse(boolean found, String hash) {
		this.found = found;
		this.hash = hash;
	}
}