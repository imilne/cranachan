package jhi.cranachan.data;

import java.io.*;
import java.util.*;
import javax.annotation.*;
import javax.faces.context.*;
import javax.faces.view.*;
import javax.inject.*;

import jhi.cranachan.bcftools.*;
import jhi.cranachan.database.*;

import jhi.flapjack.io.cmd.*;

import org.primefaces.model.*;

@Named
@ViewScoped
public class ResultsManagedBean implements Serializable
{
	// Database classes
	@Inject
	private DatasetDAO datasetDAO;
	@Inject
	private GeneDAO geneDAO;

	// Results list (used to generate table in JSF page)
	private ArrayList<GeneTableRow> tableRows = new ArrayList<>();

	// Location for storing files created by this class
	private String tmpDir;

	private List<File> mapFiles = new ArrayList<>();
	private List<File> genotypeFiles = new ArrayList<>();
	private File projectFile;

	private boolean projectFileCreated = false;
	private boolean byPosition = false;

	public ResultsManagedBean()
	{
	}

	@PostConstruct
	public void init()
	{
		// Grab the location of the temp folder so that we can write generated files to that location
		tmpDir = FacesContext.getCurrentInstance().getExternalContext().getInitParameter("tmpDir");

		// Grab the request parameters map. This should let us get at parameters which have been posted to the results page
		Map<String,String> requestParams = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();

		// We either create rows for our results table by calling code which parses parameters from search-by-position.xhtml
		if (requestParams.containsKey("by-position"))
		{
			createByPositionRows(requestParams);
			byPosition = true;
		}

		// Or we create rows for our results table by calling code which parses parameters from search-by-gene.xhtml
		else if (requestParams.containsKey("by-gene"))
			createByGeneRows(requestParams);
	}

	private void createByPositionRows(Map<String, String> requestParams)
	{
		// Retrieve parameters from the request map which have been posted to the results page
		String chromosome = requestParams.get("chr");
		long start = Long.parseLong(requestParams.get("start"));
		long end = Long.parseLong(requestParams.get("end"));
		String datasetId = requestParams.get("datasetId");

		// Get a reference to the dataset from the database (so we can run bcftools on it)
		Dataset dataset = datasetDAO.getById(datasetId);

		// Create a name for the file to be generated by bcftools
		String name = chromosome + "-" + start + "-" + end;

		// Run bcftools and generate the file
		File file = runBcfToolsView(name, dataset, chromosome, start, end);

		File stats = runBcfToolsStats(file.getName(), file);
		long snpCount = getSnpCount(stats);

		// Add the results to the list which we will use in the JSF page to generate the table
		GeneTableRow row = new GeneTableRow("", chromosome, start, end, snpCount, file);
		tableRows.add(row);

		if (snpCount > 0)
		{
			projectFileCreated = true;

			String mapFileName = changeFileExtension(file.getName(), ".map");
			File mapFile = new File(tmpDir, mapFileName);
			mapFiles.add(mapFile);

			String genotypeFileName = changeFileExtension(file.getName(), ".dat");
			File genotypeFile = new File(tmpDir, genotypeFileName);
			genotypeFiles.add(genotypeFile);

			VcfToFJTabbedConverter vcfConverter = new VcfToFJTabbedConverter(file, mapFile, genotypeFile);
			vcfConverter.convert();

			String projectFileName = changeFileExtension(file.getName(), ".flapjack");
			projectFile = new File("/tmp", projectFileName);

			String flapjackPath = FacesContext.getCurrentInstance().getExternalContext().getRealPath("/WEB-INF/lib/flapjack.jar");

			try
			{
				FlapjackCreateProject createProject = new FlapjackCreateProject(genotypeFile, projectFile)
					.withMapFile(mapFile);
				createProject.run(flapjackPath, tmpDir);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	private void createByGeneRows(Map<String, String> requestParams)
	{
		// Retrieve parameters from the request map which have been posted to the results page
		String geneList = requestParams.get("geneList");
		int extendRegion = Integer.parseInt(requestParams.get("extendRegion"));
		// TODO: our bcftools code does not currently allow us to specify a quality score filter
		int minQualScore = Integer.parseInt(requestParams.get("minQualScore"));
		String datasetId = requestParams.get("datasetId");

		// Get a reference to the dataset from the database (so we can run bcftools on it)
		Dataset dataset = datasetDAO.getById(datasetId);

		// Split the geneList by newlines (and remove any blank lines
		String[] geneNames = geneList.split("[\\r\\n]+");

		// Iterate over the gene names provided by the user and retrieve those that we can find from the database
		for(String name : geneNames)
		{
			List<Gene> genes = geneDAO.getByNameAndDatasetId(name, datasetId);

			// For each gene retrieved from the database, run bcftools and add the results to the list which we will use
			// in the JSF page to generate the table
			for(Gene gene: genes)
			{
				File file = runBcfToolsView(gene.getName(), dataset, gene.getChromosome(), gene.getStart()-extendRegion, gene.getEnd()+extendRegion);

				GeneTableRow row = new GeneTableRow(gene.getName(), gene.getChromosome(), gene.getStart(), gene.getEnd(), 0, file);
				tableRows.add(row);
			}
		}
	}

	private File runBcfToolsView(String name, Dataset dataset, String chromosomeName, long start, long end)
	{
		File output = new File(tmpDir, name + "-" + System.currentTimeMillis() + ".vcf");
		try
		{
			BcfToolsView view = new BcfToolsView(new File(dataset.getFilepath()))
				.withOnlySNPs()
				.withVCFOutput()
				.withRegions(chromosomeName, start, end)
				.withOutputFile(output);

			view.run("BCFTOOLS", tmpDir);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return output;
	}

	private File runBcfToolsStats(String name, File vcfFile)
	{
		File output = new File(tmpDir, name + ".stats");
		try
		{
			BcfToolsStats stats = new BcfToolsStats(vcfFile)
				.withOutputFile(output);

			stats.run("BCFTOOLS", tmpDir);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return output;
	}

	private long getSnpCount(File file)
	{
		long snpCount = 0;

		try (BufferedReader reader = new BufferedReader(new FileReader(file)))
		{
			String line = "";
			while ((line = reader.readLine()) != null)
			{
				if (line.startsWith("SN"))
				{
					String[] tokens = line.split("\t");
					System.out.println(String.join(",", tokens));
					if (tokens[2].equalsIgnoreCase("number of snps:"))
					{
						snpCount = Integer.parseInt(tokens[3]);
						return snpCount;
					}
				}
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

		return snpCount;
	}

	private String changeFileExtension(String filename, String extension)
	{
		String newFileName = filename;
		if (newFileName.indexOf(".") > 0)
			newFileName = newFileName.substring(0, newFileName.lastIndexOf(".")) + extension;

		return newFileName;
	}

	public DefaultStreamedContent downloadProject()
		throws Exception
	{
		return new DefaultStreamedContent(new FileInputStream(projectFile), "application/octet-stream", projectFile.getName());
	}

	public File getProjectFile()
		{ return projectFile; }

	public void setProjectFile(File projectFile)
		{ this.projectFile = projectFile; }

	public ArrayList<GeneTableRow> getTableRows()
		{ return tableRows; }

	public void setTableRows(ArrayList<GeneTableRow> tableRows)
		{ this.tableRows = tableRows; }

	public boolean isProjectFileCreated()
		{ return projectFileCreated; }

	public void setProjectFileCreated(boolean projectFileCreated)
		{ this.projectFileCreated = projectFileCreated; }

	public boolean isByPosition()
		{ return byPosition; }

	public void setByPosition(boolean byPosition)
		{ this.byPosition = byPosition; }
}