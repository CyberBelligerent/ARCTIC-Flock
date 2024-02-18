package com.rahman.arctic.flock.exceptions;

/**
 * More defined error for ARCTIC objects
 * @author SGT Rahman
 *
 */
public class ResourceAlreadyExistsException extends Exception {

	private static final long serialVersionUID = 3306629339045794855L;

	public ResourceAlreadyExistsException(String message) {
		super(message);
	}
	
}