package com.smith.photo.push.impl;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.smith.photo.push.PushConfig;
import com.smith.photo.push.Pusher;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.lang.System.*;

/**
 * Created by esmith on 7/6/16.
 */
public class MetaDataExtractorPusher implements Pusher
{
	@Override
	public void push(PushConfig config)
	{
		out.println("Processing metadata for " + config.getSourceFolder());
		List<Path> unprocessedFiles = new ArrayList<>();
		List<PushConfig> subdirectories = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(config.getSourceFolder()))
		{
			// todo inspect timezone of locale
			SimpleDateFormat dateParser = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
			SimpleDateFormat dateConverter = new SimpleDateFormat("/yyyy/yyyy-MM (MMMM)", Locale.US);
			for (Path entry : stream)
			{
				boolean processedImage = false;
				if (Files.isReadable(entry) && Files.isRegularFile(entry) &&!Files.isHidden(entry) && !isMoovSuffix(entry))
				{
					try
					{
						Metadata metadata = ImageMetadataReader.readMetadata(entry.toFile());
						for (Directory directory : metadata.getDirectories())
						{
							for (Tag tag : directory.getTags())
							{
								//out.format("[%s] - %s = %s\n", directory.getName(), tag.getTagName(), tag.getDescription());
								if( "Exif SubIFD".equals(directory.getName()) &&  "Date/Time Original".equals(tag.getTagName()))
								{
									config.setLastDate( dateParser.parse(tag.getDescription()) );
									processedImage = copyImageToDateOrganizedFolder(config, entry, config.getLastDate(), dateConverter);
									break;
								}
								else if (!isJpegFile(entry) && !isMoovSuffix(entry))
								{
									out.println(entry.getFileName() +":" + directory.getName() +" : " + tag.getTagName());
								}
							}
							if (directory.hasErrors())
							{
								for (String error : directory.getErrors())
								{
									err.format("ERROR: %s", error);
								}
							}
						}
					}
					catch (ImageProcessingException e)
					{
						err.println("Processing exception processing " + entry.toAbsolutePath());
						e.printStackTrace();
					}
					catch (ParseException e)
					{
						err.println("Parse exception processing " + entry.toAbsolutePath());
						e.printStackTrace();
					}
				}
				else if (config.getRecurse() && Files.isDirectory(entry))
				{
					out.println("Add " + entry + " to process list");
					PushConfig moreConfig = new PushConfig();
					moreConfig.setProvider(config.getProvider());
					moreConfig.setSourceFolder(entry);
					moreConfig.setDestinationFolder(config.getDestinationFolder());
					moreConfig.setOverwrite(config.getOverwrite());
					moreConfig.setRecurse(config.getRecurse());
					moreConfig.setLastDate(config.getLastDate());
					subdirectories.add(moreConfig);
				}

				if(!processedImage && !Files.isDirectory(entry) && (isMoovSuffix(entry)||isJpegFile(entry)))
				{
					unprocessedFiles.add(entry);
				}

			}
			if(unprocessedFiles != null && !unprocessedFiles.isEmpty())
			{
				err.println("This directory had issues," + config.getSourceFolder());
				for(Path unprocessed : unprocessedFiles )
				{
					err.println("    Couldn't read metadata for " + unprocessed.toAbsolutePath() + " using last found date." + dateConverter.format(config.getLastDate()));
					if(!copyImageToDateOrganizedFolder(config, unprocessed, config.getLastDate(), dateConverter))
					    err.println(unprocessed.toAbsolutePath());
				}
			}
            // now that we've read metadata, unwind the subdirectories
			if(subdirectories!=null && !subdirectories.isEmpty())
			{
				for(PushConfig pushConfig : subdirectories )
				{
					pushConfig.setLastDate(config.getLastDate());
					push(pushConfig);
				}
			}
		}
		catch (IOException e)
		{
			err.println("ERROR:ERROR: " + e.getMessage());
			e.printStackTrace();
			exit(1);
		}
	}

	private boolean isJpegFile(Path entry)
	{
		return entry.getFileName().toString().endsWith(".jpg") || entry.getFileName().toString().endsWith(".JPG");
	}

	private boolean copyImageToDateOrganizedFolder(PushConfig config, Path entry, Date orignalDate, SimpleDateFormat dateConverter)
	{
		boolean processedImage;
		assert( !Files.isDirectory(entry) );
		String xsdDateString = dateConverter.format(orignalDate);
		Path destinationDirectory = Paths.get(config.getDestinationFolder() + dateConverter.format(orignalDate) + "/");
		createParentDirectories(destinationDirectory);
		Path destinationFile = Paths.get(config.getDestinationFolder() + dateConverter.format(orignalDate) + "/" + entry.getFileName());
		fileSystemcopy(entry,destinationFile);
		processedImage = true;
		return processedImage;
	}

	private void createParentDirectories(Path destinationDirectory)
	{
		if(!Files.isDirectory(destinationDirectory))
		{
			List<Path> parents = new ArrayList<>();
			parents.add(destinationDirectory);
			while(!Files.isDirectory(parents.get(parents.size()-1).getParent()))
			{
				parents.add(parents.get(parents.size()-1).getParent());
			}
			Collections.reverse(parents);
			for(Path parentDirectory : parents )
			{
				parentDirectory.toFile().mkdir();
				out.println("mkdir " +destinationDirectory.toAbsolutePath());
			}
		}
	}

	/**
	 * java's sandboxing doesn't preserve metadata like we want it to, so filesystem time!
	 * @param source
	 * @param destination
	 */
	private void fileSystemcopy(Path source, Path destination)
	{
		try
		{
			List<String> cmdArr= new ArrayList<String>();
			cmdArr.add("cp");
			cmdArr.add(source.toRealPath().toString());
			cmdArr.add(destination.toAbsolutePath().toString());

			ProcessBuilder builder = new ProcessBuilder(cmdArr);
			File log = new File("log");
			builder.redirectErrorStream(true);
			builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
			Process p = builder.start();
			p.waitFor(50000, TimeUnit.MILLISECONDS);
			if(p.exitValue()!=0)
			{
				out.println("cp " + source.toAbsolutePath() + " " + destination.toAbsolutePath());
			}
		}
		catch (IOException e )
		{
			e.printStackTrace();
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}

	private boolean isMoovSuffix(Path moovFile)
	{
		return moovFile.toString().endsWith("mov") || moovFile.toString().endsWith("MOV");
	}
}
