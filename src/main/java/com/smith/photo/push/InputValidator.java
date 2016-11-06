package com.smith.photo.push;

/**
 * Created by esmith on 7/6/16.
 */
public interface InputValidator
{
	void validate(String[] args, PushConfig inputConfig) throws InputValidationException;
}
