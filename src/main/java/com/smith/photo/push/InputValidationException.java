package com.smith.photo.push;

/**
 * Created by esmith on 7/6/16.
 */
public class InputValidationException extends Exception
{
	public InputValidationException(String message)
	{
		super(message);
	}

	public InputValidationException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
