package com.smith.photo.push.impl;

import com.smith.photo.push.PushConfig;
import com.smith.photo.push.Pusher;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.Iterator;

import static java.lang.System.out;

/**
 * Created by esmith on 7/6/16.
 */
public class PusherImpl implements Pusher
{
	private static void readAndDisplayMetadata(Path fileName)
	{
		try
		{

			File file = fileName.toFile();
			ImageInputStream iis = ImageIO.createImageInputStream(file);
			if (iis == null) return;
			Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);

			if (readers != null && readers.hasNext())
			{

				// pick the first available ImageReader
				ImageReader reader = readers.next();

				// attach source to the reader
				reader.setInput(iis, true);

				// read metadata of first image
				IIOMetadata metadata = reader.getImageMetadata(0);

				String[] names = metadata.getMetadataFormatNames();
				int length = names.length;
				for (int i = 0; i < length; i++)
				{
					System.out.println("Format name: " + names[i]);
					displayMetadata(metadata.getAsTree(names[i]));
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	static void displayMetadata(Node root)
	{
		displayMetadata(root, 0);
	}

	static void indent(int level)
	{
		for (int i = 0; i < level; i++)
		{
			System.out.print("    ");
		}
	}

	static void displayMetadata(Node node, int level)
	{
		// print open tag of element
		indent(level);
		System.out.print("<" + node.getNodeName());
		NamedNodeMap map = node.getAttributes();
		if (map != null)
		{

			// print attribute values
			int length = map.getLength();
			for (int i = 0; i < length; i++)
			{
				Node attr = map.item(i);
				System.out.print(" " + attr.getNodeName() +
						"=\"" + attr.getNodeValue() + "\"");
			}
		}

		Node child = node.getFirstChild();
		if (child == null)
		{
			// no children, so close element and return
			System.out.println("/>");
			return;
		}

		// children, so close current tag
		System.out.println(">");
		while (child != null)
		{
			// print children recursively
			displayMetadata(child, level + 1);
			child = child.getNextSibling();
		}

		// print close tag of element
		indent(level);
		System.out.println("</" + node.getNodeName() + ">");
	}

	private static void getAttributesInBulkWay(Path file)
			throws IOException
	{
		out.printf("%nBasic attributes of '%s':%n",
				file.toString());
		PosixFileAttributes attrs = Files.readAttributes(
				file, PosixFileAttributes.class);
		out.println("directory: " + attrs.isDirectory());
		out.println("is other : " + attrs.isOther());
		out.println("regular  : " + attrs.isRegularFile());
		out.println("symlink  : " + attrs.isSymbolicLink());
		out.println("size     : " + attrs.size());
		out.println("unique id: " + attrs.fileKey());
		out.println("access time  : " + attrs.lastAccessTime());
		out.println("creation time: " + attrs.creationTime());
		out.println("modified time: " + attrs.lastModifiedTime());
		out.println("owner: " + attrs.owner());
		out.println("group: " + attrs.group());
	}

	@Override
	public void push(PushConfig config)
	{
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(config.getSourceFolder()))
		{
			for (Path entry : stream)
			{
				out.println("working on file " + entry.getFileName());
				readAndDisplayMetadata(entry);
			}
		}
		catch (IOException e)
		{
			out.println("error while retrieving configuration directory " + e.getMessage());
			e.printStackTrace();
		}
	}

}
