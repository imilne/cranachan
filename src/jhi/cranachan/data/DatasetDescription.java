package jhi.cranachan.data;

import java.util.*;

public class DatasetDescription
{
	private long markers;
	private int samples;
	private String project;
	private List<String> dataOwner;
	private String publicationStatus;
	private String bioinformaticsContact;

	public DatasetDescription()
	{
	}

	public long getMarkers()
		{ return markers; }

	public void setMarkers(long markers)
		{ this.markers = markers; }

	public int getSamples()
		{ return samples; }

	public void setSamples(int samples)
		{ this.samples = samples; }

	public String getProject()
		{ return project; }

	public void setProject(String project)
		{ this.project = project; }

	public List<String> getDataOwner()
		{ return dataOwner; }

	public void setDataOwner(List<String> dataOwner)
		{ this.dataOwner = dataOwner; }

	public String getPublicationStatus()
		{ return publicationStatus; }

	public void setPublicationStatus(String publicationStatus)
		{ this.publicationStatus = publicationStatus; }

	public String getBioinformaticsContact()
		{ return bioinformaticsContact; }

	public void setBioinformaticsContact(String bioinformaticsContact)
		{ this.bioinformaticsContact = bioinformaticsContact; }
}