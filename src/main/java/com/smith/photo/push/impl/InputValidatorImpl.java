package com.smith.photo.push.impl;

import com.smith.photo.push.InputValidationException;
import com.smith.photo.push.InputValidator;
import com.smith.photo.push.PushConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

/**
 * Created by esmith on 7/6/16.
 */
public class InputValidatorImpl implements InputValidator
{
	private static boolean checkDirectory(Path directory)
	{
		return Files.exists(directory);
	}

	@Override
	public void validate(String[] args, PushConfig inputConfig) throws InputValidationException
	{
		if (inputConfig == null)
		{
			throw new InputValidationException("Mush initialize inputConfig reference");
		}
		if (args.length !=5 && args.length != 4 && args.length != 3 && args.length != 2)
		{
			throw new InputValidationException("Wrong arguments\n photo-push source destination [overwrite=false]");
		}
		String sourceDirectory;
		String destinationDirectory;

		sourceDirectory = args[0];
		destinationDirectory = args[1];
		if (args.length > 2)
		{
			inputConfig.setOverwrite(Boolean.valueOf(args[2]));
		}
		if (args.length > 3)
		{
			inputConfig.setRecurse(Boolean.valueOf(args[3]));
		}
		if(args.length > 4)
		{
			if("JAI".equals(args[4]) || "META".equals(args[4]))
			{
				inputConfig.setProvider(args[4]);
			}
			else
			{
				throw new InputValidationException("provider must be JAI or META");
			}
		}

		Path sourceFolder = Paths.get(sourceDirectory);
		if (!checkDirectory(sourceFolder))
		{
			throw new InputValidationException("ERROR: sourceFolder does not exist: " + sourceFolder);
		}
		inputConfig.setSourceFolder(sourceFolder);

		Path destinationFolder = Paths.get(destinationDirectory);
		if (!checkDirectory(destinationFolder))
		{
			throw new InputValidationException("ERROR: destination folder does not exist: " + destinationFolder);
		}
		inputConfig.setDestinationFolder(destinationFolder);
		inputConfig.setLastDate(new Date());
	}
}