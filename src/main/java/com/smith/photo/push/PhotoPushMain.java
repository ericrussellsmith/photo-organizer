package com.smith.photo.push;

import com.smith.photo.push.impl.InputValidatorImpl;
import com.smith.photo.push.impl.MetaDataExtractorPusher;
import com.smith.photo.push.impl.PusherImpl;

import static java.lang.System.exit;
import static java.lang.System.out;

public class PhotoPushMain
{
	static PushConfig runConfig = new PushConfig();
	//todo autowire
	static InputValidator validator = new InputValidatorImpl();
	static Pusher pusher = new PusherImpl();
	static Pusher metapusher = new MetaDataExtractorPusher();

	public static void main(String[] args)
	{
		try
		{
			validator.validate(args, runConfig);
			if("META".equals(runConfig.getProvider()))
			{
				metapusher.push(runConfig);
			}
			else
			{
				pusher.push(runConfig);
			}
		}
		catch (InputValidationException e)
		{
			out.println(e.getMessage());
			exit(1);
		}
		exit(0);
	}
}
