package it.uniroma2.isw2.milestone1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.revwalk.RevCommit;

public class Main {
	
	private static final Logger logger = LogManager.getLogger(Main.class);
	
	private static String readAll(Reader rd) throws IOException {
		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
		}
		return sb.toString();
	}

	public static JSONArray readJsonArrayFromUrl(String url) throws IOException, JSONException {
		try (InputStream is = new URL(url).openStream()) {
			BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
			String jsonText = readAll(rd);
			return new JSONArray(jsonText);
		}
	}

	public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
		try (InputStream is = new URL(url).openStream();) {
			BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
			String jsonText = readAll(rd);
			return new JSONObject(jsonText);
		}
	}

	public static JSONArray retrieveJSONFromJira(String projName, String issueType, String resolution) 
			throws IOException, JSONException {
		Integer j = 0;
		Integer i = 0;
		Integer total = 1;
		JSONArray jiraResults = new JSONArray();
		do {
			//Only gets a max of 1000 at a time, so must do this multiple times if bugs > 1000
			j = i + 1000;
			String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
					+ projName + "%22AND%22issueType%22=%22" + issueType + "%22AND(%22status%22=%22closed%22OR"
					+ "%22status%22=%22resolved%22)AND%22resolution%22=%22" + resolution
					+ "%22&fields=key,resolutiondate,versions,created&startAt="
					+ i.toString() + "&maxResults=" + j.toString();
			JSONObject json = readJsonFromUrl(url);
			JSONArray issues = json.getJSONArray("issues");
			total = json.getInt("total");
			for (; i < total && i < j; i++) {
				//Storing each result in an array
				jiraResults.put(issues.getJSONObject(i)); 
			}  
		} while (i < total);
		
		return jiraResults;
	}
	
	public static void writeFormatted(FileWriter f, String issueid, Date date) throws IOException {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		int month = cal.get(Calendar.MONTH) + 1;
		int year = cal.get(Calendar.YEAR);
		f.append(issueid + "," + month + "/" + year + "\n");
	}
	
	public static Date getFixDate(String key, Git git)
			throws NoHeadException, GitAPIException, IOException {
		//do git log --grep=ticketID -> search for all commits containing that ticket id
		Iterable <RevCommit>commits = git.log().all().call();  
		Date fixDate = new Date(0);
		for (RevCommit commit : commits) {
			if (commit.getFullMessage().contains(key)) {
				//the fix date is the last date containing that id in the commit message
				Date commitDate = commit.getCommitterIdent().getWhen();
				if (fixDate.compareTo(commitDate) < 0) {
					fixDate = commitDate;
				}	
			}
		}
		return fixDate;
	}
	
	public static void main(String[] args) 
			throws IOException, JSONException, NoHeadException, GitAPIException {
		
		logger.info("Retieving data from Jira...");
		JSONArray fixedBugs = retrieveJSONFromJira("VCL", "Bug", "fixed");
		logger.info("Found " + fixedBugs.length() + " fixed bugs in Jira DB!");
		
		logger.info("Downloading repository from Git...");
		Git git = Git.cloneRepository().setURI("https://gitbox.apache.org/repos/asf/vcl.git")
		  .setDirectory(new File("data/vcl.git"))
		  .setCloneAllBranches(true)
		  .call();
		
		//Detect date of first and last commit
		Iterable<RevCommit> commits = git.log().all().call();
		Date firstDate = new Date();
		Date lastDate = new Date(0);
		int results = 0;
		for (RevCommit commit : commits) {
			Date commitDate = commit.getCommitterIdent().getWhen();
			if ( firstDate.compareTo(commitDate) > 0 ) {
				firstDate = commitDate;
			}
			if ( lastDate.compareTo(commitDate) < 0 ) {
				lastDate = commitDate;
			}
			results++;
		}	
		logger.info("Done! Found " + results + " commits in Git!");
		
		//Formatting data to export a CSV file
		try ( FileWriter csvWriter = new FileWriter("data/vcl.csv") ) {
			csvWriter.append("Ticket ID,Fix Date\n");
			writeFormatted(csvWriter, "VCL-first", firstDate);
			writeFormatted(csvWriter, "VCL-last", lastDate);
				
			//for each fixed bug, get the ID and the fix date
			for (int z=0; z < fixedBugs.length(); z++) {
				String bugID = fixedBugs.getJSONObject(z).get("key").toString();			
				Date fixDate = getFixDate(bugID, git);
				//write that date in CSV, only if there is a commit with that ID in Git
				Date defaultDate = new Date(0);
				if (fixDate.compareTo(defaultDate) != 0) {
					writeFormatted(csvWriter, bugID, fixDate);
				}
			}		
			csvWriter.flush();
		} finally {
			logger.info("Done! Your file .csv is ready!");
		}
	}
}
		
