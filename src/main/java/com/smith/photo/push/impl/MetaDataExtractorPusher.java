package com.smith.photo.push.impl;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.smith.photo.push.PushConfig;
import com.smith.photo.push.Pusher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.io.File;
import java.io.IOException;
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
	private final static Logger LOGGER = LogManager.getLogger( MetaDataExtractorPusher.class );

	// todo inspect timezone of locale
	// Mon Sep 28 08:44:14 -06:00 2015
	// Mon Nov 16 15:04:40 -07:00 2015
	// Wed Aug 26 16:58:32 -06:00 2015
	SimpleDateFormat pngDateParser = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.US);
	SimpleDateFormat dateParser = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
	SimpleDateFormat dateConverter = new SimpleDateFormat("/yyyy/yyyy-MM (MMMM)", Locale.US);
	int copiedFiles = 0;
	int changedFiles = 0;
	int processedFiles = 0;
	int readFiles = 0;
	int directories = 0;
	@Override
	public void push(PushConfig config)
	{
		LOGGER.info("Processing metadata for " + config.getSourceFolder());
		++directories;
		if(directories%10==0)
			LOGGER.info("directories: "+directories);
		List<Path> unprocessedFiles = new ArrayList<>();
		List<PushConfig> subdirectories = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(config.getSourceFolder()))
		{
			for (Path entry : stream)
			{
				boolean processedImage = false;
				if (Files.isReadable(entry) && Files.isRegularFile(entry) &&!Files.isHidden(entry) && !isMoovSuffix(entry))
				{
					++readFiles;
					if(readFiles%100==0)
						LOGGER.info("read: "+readFiles);
					try
					{
						Metadata metadata = ImageMetadataReader.readMetadata(entry.toFile());
						for (Directory directory : metadata.getDirectories())
						{
							for (Tag tag : directory.getTags())
							{
								if( isJpegFile(entry) && "Exif SubIFD".equals(directory.getName()) &&  "Date/Time Original".equals(tag.getTagName()))
								{
									config.setLastDate( dateParser.parse(tag.getDescription()) );
									processedImage = copyImageToDateOrganizedFolder(config, entry, config.getLastDate(), dateConverter);
									break;
								}
								else if ( isPngFile(entry) && "File".equals(directory.getName()) && "File Modified Date".equals(tag.getTagName()))
								{
									//Mon Nov 16 15:04:40 -07:00 2015
									String dateStr=tag.getDescription().replaceAll("\\s-?\\+?\\d\\d:\\d\\d\\s"," ");
									config.setLastDate( pngDateParser.parse(dateStr) );
									processedImage = copyImageToDateOrganizedFolder(config, entry, config.getLastDate(), dateConverter);
									break;
								}
								else if (!isJpegFile(entry) && !isMoovSuffix(entry) && !isPngFile(entry))
								{
									LOGGER.info(entry.getFileName() +":" + directory.getName() +" : " + tag.getTagName() +":" + tag.getDescription());
								}
							}
							if (directory.hasErrors())
							{
								for (String error : directory.getErrors())
								{
									LOGGER.error("ERROR: " + error);
								}
							}
						}
					}
					catch (ImageProcessingException e)
					{
						LOGGER.error("Processing exception processing " + entry.toAbsolutePath(),e);
					}
					catch (ParseException e)
					{
						LOGGER.error("Parse exception processing " + entry.toAbsolutePath(),e);
					}
				}
				else if (config.getRecurse() && Files.isDirectory(entry))
				{
					LOGGER.info("Add " + entry + " to process list");
					PushConfig moreConfig = new PushConfig();
					moreConfig.setProvider(config.getProvider());
					moreConfig.setSourceFolder(entry);
					moreConfig.setDestinationFolder(config.getDestinationFolder());
					moreConfig.setOverwrite(config.getOverwrite());
					moreConfig.setRecurse(config.getRecurse());
					moreConfig.setLastDate(config.getLastDate());
					subdirectories.add(moreConfig);
				}

				if(!processedImage && !Files.isDirectory(entry) && (isMoovSuffix(entry)||isJpegFile(entry)||isPngFile(entry)))
				{
					unprocessedFiles.add(entry);
				}

			}
			if(unprocessedFiles != null && !unprocessedFiles.isEmpty())
			{
				LOGGER.warn("This directory had issues," + config.getSourceFolder());
				for(Path unprocessed : unprocessedFiles )
				{
					LOGGER.warn("    Couldn't read metadata for " + unprocessed.toAbsolutePath() + " using last found date." + dateConverter.format(config.getLastDate()));
					if(!copyImageToDateOrganizedFolder(config, unprocessed, config.getLastDate(), dateConverter))
					    LOGGER.error(unprocessed.toAbsolutePath());
					++readFiles;
				}
			}
			LOGGER.info(config.getSourceFolder());
			LOGGER.info("Copied " + copiedFiles + " files.");
			LOGGER.info("Modified " +  changedFiles + " files.");
			LOGGER.info("Processed " +  processedFiles + " files.");
			LOGGER.info("Read " +  readFiles + " files.");
			LOGGER.info("Directories " +  directories + " directories.");
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
			LOGGER.error("ERROR:ERROR: " + e.getMessage(), e);
			exit(1);
		}
	}

	private boolean copyImageToDateOrganizedFolder(PushConfig config, Path entry, Date orignalDate, SimpleDateFormat dateConverter)
	{
		boolean processedImage;
		assert( !Files.isDirectory(entry) );
		Path destinationDirectory = Paths.get(config.getDestinationFolder() + dateConverter.format(orignalDate) + "/");
		createParentDirectories(destinationDirectory);
		Path destinationFile = Paths.get(config.getDestinationFolder() + dateConverter.format(orignalDate) + "/" + entry.getFileName());
		fileSystemcopy(entry,destinationFile,orignalDate);
		processedImage = true;
		++processedFiles;
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
				LOGGER.info("mkdir " +destinationDirectory.toAbsolutePath());
			}
		}
	}

	/**
	 * java's sandboxing doesn't preserve metadata like we want it to, so filesystem time!
	 * @param source
	 * @param destination
	 */
	private void fileSystemcopy(Path source, Path destination,Date orignalDate)
	{
		try
		{
			File log = new File("log");
			if(!Files.exists(destination))
			{
				copyFile(source, destination, log);
				++copiedFiles;
				if(copiedFiles%100 == 0)
				{
					LOGGER.info("copied: " + copiedFiles);
				}
			}

			setFileDates(destination, orignalDate, log);
		}
		catch (IOException e )
		{
			LOGGER.error(e);
		}
		catch (InterruptedException e)
		{
			LOGGER.error(e);
		}
	}

	private void setFileDates(Path destination, Date orignalDate, File log) throws IOException, InterruptedException
	{
		List<String> cmdArr= new ArrayList<String>();
		cmdArr.add("touch");
		cmdArr.add("-t");
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmm");
		cmdArr.add(format.format(orignalDate));
		cmdArr.add(destination.toAbsolutePath().toString());
		ProcessBuilder builder = new ProcessBuilder(cmdArr);
		builder.redirectErrorStream(true);
		builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
		Process p = builder.start();
		p.waitFor(50000, TimeUnit.MILLISECONDS);
		if(p.exitValue()!=0)
		{
			LOGGER.error( cmdArr + System.lineSeparator() + "touch -t " + " " + format.format(orignalDate)  + " " + destination.toAbsolutePath() + " exit code " + p.exitValue());
		}
		else
		{
			++changedFiles;
			if(changedFiles%100 == 0)
			{
				LOGGER.info("changed: " + changedFiles);
			}
		}
	}

	private void copyFile(Path source, Path destination, File log) throws IOException, InterruptedException
	{
		List<String> cmdArr= new ArrayList<String>();
		cmdArr.add("cp");
		cmdArr.add("-p");
		cmdArr.add(source.toRealPath().toString());
		cmdArr.add(destination.toAbsolutePath().toString());

		ProcessBuilder builder = new ProcessBuilder(cmdArr);
		builder.redirectErrorStream(true);
		builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
		Process p = builder.start();
		p.waitFor();
		if(p.exitValue()!=0)
		{
			LOGGER.error(cmdArr + System.lineSeparator() +  "cp " + source.toAbsolutePath() + " " + destination.toAbsolutePath() + " exit code: " + p.exitValue() );
		}
		else
		{
			LOGGER.info( cmdArr );
		}
	}

	private boolean isMoovSuffix(Path moovFile)
	{
		return moovFile.toString().endsWith("mov") || moovFile.toString().endsWith("MOV");
	}

	private boolean isPngFile(Path entry)
	{
		return entry.getFileName().toString().endsWith(".png") || entry.getFileName().toString().endsWith(".PNG");
	}

	private boolean isJpegFile(Path entry)
	{
		return entry.getFileName().toString().endsWith(".jpg") || entry.getFileName().toString().endsWith(".JPG");
	}
}
