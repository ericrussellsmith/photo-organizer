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
				/*
				else if ( isMoovSuffix(entry))
				{
					Calendar c = moovFileToCal(entry);
					SimpleDateFormat dateConverter = new SimpleDateFormat("/yyyy/yyyy-MM (MMMM)", Locale.US);
					Path destinationDirectory = Paths.get(config.getDestinationFolder() + dateConverter.format(c.getTime()) + "/");
					createParentDirectories(destinationDirectory);
					Path destinationFile = Paths.get(destinationDirectory + "/" + entry.getFileName());
					out.println("MOOV:"+destinationFile);
					fileSystemcopy(entry,destinationFile);
				}
                */

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

    private Calendar moovFileToCal( Path moovFile) throws IOException
    {
	    assert isMoovSuffix(moovFile);
	    //FileInputStream fileInputStream = new FileInputStream(moovFile);
	    RandomAccessFile raf = new RandomAccessFile(moovFile.toFile(),"r");
	    return moovBytesToCal2(raf);
	    //byte[] bytes = new byte[10000];
	    //fileInputStream.read(bytes);
	    //return moovBytesToCal(bytes);
    }

	private boolean isMoovSuffix(Path moovFile)
	{
		return moovFile.toString().endsWith("mov") || moovFile.toString().endsWith("MOV");
	}

	private Calendar moovBytesToCal(byte[] buffer) {
		int i = 0;
		int pos = 0;
		int atom_size=8;
		String movatom = "moov";
		while(pos+atom_size+atom_size<buffer.length)
		{
			byte[] readbuffer =  Arrays.copyOfRange(buffer,pos,pos+atom_size);
			BigInteger atomSizeBI = new BigInteger (readbuffer);
			long atomSize = atomSizeBI.longValue();
			out.println("atom size; "+ atomSize);
			if (readbuffer.length==atom_size)
			{
				byte[] atomName = Arrays.copyOfRange(readbuffer, 4, 8);
				//if ( Arrays.equals(atomName,movatom) )
				{
					out.println("found moov atom: " + readbuffer);
					break;
				}
			}
			pos+=atom_size;
			out.println(readbuffer);
		}
		exit(0);


		i += unsignedByteToInt(buffer[pos++]) << 24;
		i += unsignedByteToInt(buffer[pos++]) << 16;
		i += unsignedByteToInt(buffer[pos++]) << 8;
		i += unsignedByteToInt(buffer[pos++]) << 0;
		long l = unsignedIntToLong(i);

		long days = l / 86400;
		long time = l % 86400;
		long hours = time / (60 * 60);
		long minutes = (time % (60 * 60)) / 60;
		long seconds = time % 60;

		GregorianCalendar cal = new GregorianCalendar(1904, 0, 1);
		cal.add(Calendar.DAY_OF_MONTH, (int)days);
		cal.set(Calendar.HOUR_OF_DAY, (int)hours);
		cal.set(Calendar.MINUTE, (int)minutes);
		cal.set(Calendar.SECOND, (int)seconds);

		return cal;
	}

	private class ParsedAtom
	{

	}

	private Calendar moovBytesToCal2(RandomAccessFile raf) throws IOException
    {
	    long off = 0;
	    long stopAt = 10000;
	    byte[] atomSizeBuf = new byte[1000];
	    byte[] atomTypeBuf = new byte[1000];
	    byte[] extendedAtomSizeBuf = new byte[10000];
	    List<ParsedAtom> parsedAtomList = new ArrayList<>();
	    while (off < stopAt)
	    {
		raf.seek (off);

		// 1. first 32 bits are atom size
		// use BigInteger to convert bytes to long
		// (instead of signed int)
		int bytesRead = raf.read (atomSizeBuf, 0,
				atomSizeBuf.length);
		if (bytesRead < atomSizeBuf.length)
			throw new IOException ("couldn't read atom length");
		BigInteger atomSizeBI = new BigInteger (atomSizeBuf);
		long atomSize = atomSizeBI.longValue();

		// this is kind of a hack to handle the udta problem
		// (see below) when the parent didn't have children,
		// meaning we've read 4 bytes of 0 and the parent atom
		// is already over
		if (raf.getFilePointer() == stopAt)
			break;

		// 2. next, the atom type
		bytesRead = raf.read (atomTypeBuf, 0, atomTypeBuf.length);
		if (bytesRead != atomTypeBuf.length)
			throw new IOException ("Couldn't read atom type");
		String atomType = new String (atomTypeBuf);

		// 3. if atomSize was 1, then this is 64-bit ext size
		if (atomSize == 1) {
			bytesRead = raf.read (extendedAtomSizeBuf, 0,
					extendedAtomSizeBuf.length);
			if (bytesRead != extendedAtomSizeBuf.length)
				throw new IOException (
						"Couldn't read extended atom size");
			BigInteger extendedSizeBI =
					new BigInteger (extendedAtomSizeBuf);
			atomSize = extendedSizeBI.longValue();
		}

		// if this atom size is negative, or extends past end
		// of file, it's extremely suspicious (i.e.,we're not
		// really in a quicktime file)
		if ((atomSize < 0)  ||
				((off + atomSize) > raf.length()))
			throw new IOException (
					"atom has invalid size: " + atomSize);

		// 4. if a container atom, then parse the children
		    /*
		ParsedAtom parsedAtom = null;
		if (ATOM_CONTAINER_TYPES.contains (atomType)) {
			// children run from current point to end of the atom
			ParsedAtom [] children =
					parseAtoms (raf, raf.getFilePointer(), off + atomSize);
			parsedAtom =
					new ParsedContainerAtom (atomSize, atomType, children);
		} else {
			parsedAtom =
					AtomFactory.getInstance().createAtomFor (
							atomSize, atomType, raf);
		}

		// add atom to the list
		parsedAtomList.add (parsedAtom);
*/
		// now set offset to next atom (or end-of-file
		// in special case (atomSize = 0 means atom goes
		// to EOF)
		if (atomSize == 0)
			off = raf.length();
		else
			off += atomSize;

		// if a 'udta' container atom, then jump ahead 4
		// to work around Apple's QT 1.0 workaround
		// (http://developer.apple.com/technotes/qt/qt_03.html )
		if (atomType.equals("udta"))
			off += 4;

	} // while not at stopAt
	    int days=0,hours=0,minutes=0,seconds=0;
	    GregorianCalendar cal = new GregorianCalendar(1904, 0, 1);
	    cal.add(Calendar.DAY_OF_MONTH, (int)days);
	    cal.set(Calendar.HOUR_OF_DAY, (int)hours);
	    cal.set(Calendar.MINUTE, (int)minutes);
	    cal.set(Calendar.SECOND, (int)seconds);
	    return cal;
}


	private static int unsignedByteToInt(byte b) {
		return b & 0xFF;
	}
	private static long unsignedIntToLong(int i)
	{
		return i & 0xffffffffL;
	}
}
