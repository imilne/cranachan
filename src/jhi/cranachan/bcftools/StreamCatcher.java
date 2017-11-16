// Copyright 2017 Information & Computational Sciences, JHI. All rights
// reserved. Use is subject to the accompanying licence terms.

package jhi.cranachan.bcftools;

import java.io.*;
import java.util.*;
import java.util.logging.*;

abstract class StreamCatcher extends Thread
{
	private Logger LOG = Logger.getLogger(StreamCatcher.class.getName());

	private BufferedReader reader = null;

	public StreamCatcher(InputStream in)
	{
		reader = new BufferedReader(new InputStreamReader(in));
		start();
	}

	public void run()
	{
		try
		{
			String line = reader.readLine();
			StringTokenizer st = null;

			while (line != null)
			{
				processLine(line);
				line = reader.readLine();
			}
		}
		catch (Exception e) {
			LOG.log(Level.SEVERE, e.getMessage(), e);
		}

		try { reader.close(); }
		catch (IOException e) {}
	}

	protected abstract void processLine(String line)
		throws Exception;
}