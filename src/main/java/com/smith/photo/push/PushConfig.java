package com.smith.photo.push;

import java.nio.file.Path;
import java.util.Date;

/**
 * Created by esmith on 7/6/16.
 */
public class PushConfig
{
	Path destinationFolder;
	Path sourceFolder;
	private Boolean overwrite = false;
	private Boolean recurse = true;
	private String provider;
	private Date lastDate = new Date();

	public Date getLastDate()
	{
		return lastDate;
	}

	public void setLastDate(Date lastDate)
	{
		this.lastDate = lastDate;
	}

	public String getProvider()
	{
		return provider;
	}

	public void setProvider(String provider)
	{
		this.provider = provider;
	}

	public Boolean getOverwrite()
	{
		return overwrite;
	}

	public void setOverwrite(Boolean overwrite)
	{
		this.overwrite = overwrite;
	}

	public Path getSourceFolder()
	{
		return sourceFolder;
	}

	public void setSourceFolder(Path sourceFolder)
	{
		this.sourceFolder = sourceFolder;
	}

	public Path getDestinationFolder()
	{
		return destinationFolder;
	}

	public void setDestinationFolder(Path destinationFolder)
	{
		this.destinationFolder = destinationFolder;
	}

	public Boolean getRecurse()
	{
		return recurse;
	}

	public void setRecurse(Boolean recurse)
	{
		this.recurse = recurse;
	}
}
