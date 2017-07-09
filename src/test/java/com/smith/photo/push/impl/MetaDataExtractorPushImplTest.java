package com.smith.photo.push.impl;

import junit.framework.TestCase;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by esmith on 7/8/17.
 */
public class MetaDataExtractorPushImplTest extends TestCase
{
	MetaDataExtractorPusher pusher;

	@Override
	protected void setUp() throws Exception
	{
		super.setUp();
		pusher = new MetaDataExtractorPusher();
	}

	public void testDateFormat()
	{
		//SimpleDateFormat pngDateParser2 = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.US);
		String dateStr = "Wed Aug 26 16:58:32 -06:00 2015";
		dateStr=dateStr.replaceAll("\\s-?\\+\\d\\d:\\d\\d\\s"," ");
		try
		{
			pusher.pngDateParser.parse(dateStr);
		}
		catch (ParseException pe)
		{
			fail("unable to parse:" +dateStr);
		}
	}

	public void testDateFormat2()
	{
		//SimpleDateFormat pngDateParser2 = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.US);
		String dateStr = "Wed Aug 26 16:58:32 +06:00 2015";
		dateStr=dateStr.replaceAll("\\s-?\\+?\\d\\d:\\d\\d\\s"," ");
		try
		{
			pusher.pngDateParser.parse(dateStr);
		}
		catch (ParseException pe)
		{
			fail("unable to parse:" +dateStr);
		}
	}

	public void testDateFormat3()
	{
		//SimpleDateFormat pngDateParser2 = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.US);
		String dateStr = "Wed Aug 26 16:58:32 06:00 2015";
		dateStr=dateStr.replaceAll("\\s-?\\+?\\d\\d:\\d\\d\\s"," ");
		try
		{
			pusher.pngDateParser.parse(dateStr);
		}
		catch (ParseException pe)
		{
			fail("unable to parse:" +dateStr);
		}
	}

}
