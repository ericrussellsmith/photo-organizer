package com.smith.photo.push.impl;

import com.smith.photo.push.InputValidationException;
import junit.framework.TestCase;

/**
 * Created by esmith on 7/6/16.
 */
public class InputValidatorImplTest extends TestCase
{
	private InputValidatorImpl validator;
	@Override
	protected void setUp() throws Exception
	{
		super.setUp();
		validator = new InputValidatorImpl();
	}

	public void testEmptyInput()
	{
        try
        {
	        validator.validate(null,null);
	        fail("expected failure, null validation args");
        }
        catch (InputValidationException ive)
        {

        }
	}
}
