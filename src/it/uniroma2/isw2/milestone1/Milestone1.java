package it.uniroma2.isw2.milestone1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

public class Milestone1 {
	
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
			BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
			String jsonText = readAll(rd);
			JSONArray json = new JSONArray(jsonText);
			return json;
		}
	}

	public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
		try (InputStream is = new URL(url).openStream();) {
			BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
			String jsonText = readAll(rd);
			return new JSONObject(jsonText);
		}
	}

	
	public static void main(String[] args) throws IOException, JSONException {
		// Part 1: Retrieving from Jira all the JSON fixed bugs of the VCL project
		String projName ="VCL";
		Integer j = 0;
		Integer i = 0;
		Integer total = 1;
		JSONArray fixedBugs = new JSONArray();
		do {
			//Only gets a max of 1000 at a time, so must do this multiple times if bugs > 1000
			j = i + 1000;
			String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
					+ projName + "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR"
					+ "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22&fields=key,resolutiondate,versions,created&startAt="
					+ i.toString() + "&maxResults=" + j.toString();
			JSONObject json = readJsonFromUrl(url);
			JSONArray issues = json.getJSONArray("issues");
			total = json.getInt("total");
			for (; i < total && i < j; i++) {
				//Storing each JSON fixed bug in an array
				fixedBugs.put(issues.getJSONObject(i)); 
			}  
		} while (i < total);
		
		System.out.println("Found " + fixedBugs.length() + " fixed bugs in Jira DB!");
		
		// Part 2: Get the fix date of every bug from Git and store it in a CSV file
		try  {
			//to clone the repository in the data folder (use only first time)
			/*Git git = Git.cloneRepository()
					  .setURI("https://gitbox.apache.org/repos/asf/vcl.git")
					  .setDirectory(new File("C:/Users/domen/eclipse-workspace/Milestone1/data/vcl.git"))
					  .setCloneAllBranches(true)
					  .call();*/

			//if the repository is already cloned
			Git git = Git.init().setDirectory(new File("C:/Users/domen/eclipse-workspace/"
					+ "Milestone1/data/vcl.git")).call();
			
			//formatting data to export a CSV
			FileWriter csvWriter = new FileWriter("C:/Users/domen/eclipse-workspace/"
					+ "Milestone1/data/vcl.csv");
			csvWriter.append("Ticket ID;" + "Fix Date\n");
			
			//iterate over commits to get first and last commit date
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
			
			System.out.println("Found " + results + " commits in Git!");
			
			csvWriter.append("VCL-first;" + (firstDate.getMonth() + 1) + 
					"/" + (firstDate.getYear() + 1900) + "\n");
			csvWriter.append("VCL-last;" + (lastDate.getMonth() + 1) + 
					"/" + (lastDate.getYear() + 1900) + "\n");
			
			//for each fixed bug, take the ticket id 
			for (int z=0; z < fixedBugs.length(); z++) {
				String key = fixedBugs.getJSONObject(z).get("key").toString();			
				//do git log --grep=ticketID -> search for all commits containing that ticket id
				commits = git.log().all().call();  
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
				//write results in CSV
				Date defaultDate = new Date(0);
				if (fixDate.compareTo(defaultDate) != 0) {
					csvWriter.append(key + ";" + (fixDate.getMonth() + 1) + 
							"/" + (fixDate.getYear() + 1900) + "\n");
				}
			}
			csvWriter.flush();
			csvWriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		} 
		System.out.println("Il tuo file CSV è pronto!");
		return;
	}
}
		
