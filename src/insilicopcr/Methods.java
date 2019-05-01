package insilicopcr;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.Arrays;

public class Methods {
	
	private static HashMap<Character, Character[]> degenerates = new HashMap<Character, Character[]>();
	private static Pattern degenRegex;
	
	// Used to ensure a file is in fasta format, at least that it starts with a ">"
	public static boolean verifyFastaFormat(File checkFile) {
		String line;
		BufferedReader reader;
		try {
			String[] fileParts = checkFile.getName().split("\\.");
			
			// User can submit gzipped files, so if they do, read it appropriately
			if(fileParts[fileParts.length - 1].equals("gz")) {
				reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(checkFile))));
			}else {
				reader = new BufferedReader(new FileReader(checkFile));
			}
			try {
				line = reader.readLine();
				char[] characters = line.toCharArray();
				if(characters[0] == '>' || characters[0] == '@') {
					reader.close();
					return true;
				}
				reader.close();
			}catch(IOException e) {
				e.printStackTrace();
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	// Create a list of samples
	public static HashMap<String, Sample> createSampleDict(File inputFile) {
		HashMap<String, Sample> sampleDict = new HashMap<String, Sample>();
		if(inputFile.isDirectory()) {
			for(File entry : inputFile.listFiles()) {
				String entryName = entry.getName().split("\\.fasta")[0];
				entryName = entryName.split("\\.fastq")[0];
				entryName = entryName.replaceAll("_R1", "");
				entryName = entryName.replaceAll("_R2", "");
				if(!sampleDict.isEmpty()) {
					boolean unique = true;
					for(String key : sampleDict.keySet()) {
						Sample checkSample = sampleDict.get(key);
						if(checkSample.getName().equals(entryName)) {
							unique = false;
							checkSample.addFile(entry.getAbsolutePath()); // Add the additional file path to the sample's file list
							// Attempt to ensure the file list has R1 and R2 in the correct order
							ArrayList<String> filesList = checkSample.getFiles();
							Collections.sort(filesList);
							checkSample.setFiles(filesList);							
							break;
						}
					}
					if(unique) {
						Sample sample = new Sample();
						sample.setName(entryName);
						if(entry.getName().contains(".fasta")) {
							sample.setFileType("fasta");
						}else if(entry.getName().contains(".fastq")) {
							sample.setFileType("fastq");
						}
						sample.addFile(entry.getAbsolutePath()); // First instance of sample, add the file path to the new sample's file list
						sampleDict.put(entryName, sample);
					}
				}else {
					Sample sample = new Sample();
					sample.setName(entryName);
					if(entry.getName().contains(".fasta")) {
						sample.setFileType("fasta");
					}else if(entry.getName().contains(".fastq")) {
						sample.setFileType("fastq");
					}
					sample.addFile(entry.getAbsolutePath()); // First file checked, add the file path to the new sample's file list
					sampleDict.put(entryName, sample);
				}
			}
		}else {
			Sample sample = new Sample();
			String sampleName = inputFile.getName().split("\\.")[0];
			sample.setName(sampleName);
			sample.setFile(inputFile.getAbsolutePath());
			if(inputFile.getName().contains(".fasta")) {
				sample.setFileType("fasta");
			}else if(inputFile.getName().contains(".fastq")) {
				sample.setFileType("fastq");
			}
			sampleDict.put(sampleName, sample);
		}
		return sampleDict;
	}
	
	// Parse a fasta file into a dictionary, where the ID is the key value for the sequence
	public static HashMap<String, String> parseFastaToDictionary(File file){
		
		HashMap<String, String> fastaDict = new HashMap<String, String>();
		
		// First read in all lines
		String line;
		ArrayList<String> lines = new ArrayList<String>();
		try (BufferedReader reader = new BufferedReader(new FileReader(file))){
			try {
				while((line = reader.readLine()) != null) {
					if(!line.isEmpty()) {
						lines.add(line);
					}
				}
			}catch(IOException e) {
				e.printStackTrace();
			}
			reader.close();
		}catch(IOException e) {
			e.printStackTrace();
		}
		// This will recreate the file in memory
		String joinedLines = String.join("\n", lines);
		
		//Split into entries
		String[] splitEntries = joinedLines.split(">");
		for(String entry : splitEntries) {
			
			//Get rid of first entry from the split, it will contain nothing
			if(!entry.isEmpty()) {
				String[] splitEntry = entry.split("\n");
				String id = splitEntry[0];
				String seq = splitEntry[1];
				
				// Put the entry into the dictionary
				fastaDict.put(id, seq);
			}
		}
		
		// Return the filled dictionary
		return fastaDict;
	}
	
	// Process the primers in the primer dictionary
	public static void processPrimers(HashMap<String, String> primerDict, TextArea outputField, File outDir, String sep) {
		
		// Need to generate the degen Regex
		// Unfortunately cannot convert directly from Object[] to char[], or even from Character[] to char[]
		degenerates.put('R', new Character[] {'A', 'G'});
		degenerates.put('Y', new Character[] {'C', 'T'});
		degenerates.put('S', new Character[] {'G', 'C'});
		degenerates.put('W', new Character[] {'A', 'T'});
		degenerates.put('K', new Character[] {'G', 'T'});
		degenerates.put('M', new Character[] {'A', 'C'});
		degenerates.put('B', new Character[] {'G', 'C', 'T'});
		degenerates.put('D', new Character[] {'A', 'G', 'T'});
		degenerates.put('H', new Character[] {'A', 'C', 'T'});
		degenerates.put('V', new Character[] {'A', 'C', 'G'});
		degenerates.put('N', new Character[] {'A', 'C', 'G', 'T'});
		Character[] degenCharArray = degenerates.keySet().toArray(new Character[degenerates.keySet().size()]);
		char[] charDegen = new char[degenCharArray.length];
		for(int i = 0; i < charDegen.length; i++) {
			charDegen[i] = (char)degenCharArray[i];
		}
		String degenRegexString = String.join("", new String(charDegen));
		degenRegex = Pattern.compile("[" + degenRegexString + "]");
		
		// This regex will find any incompatible characters in the primer sequences
		Pattern regex = Pattern.compile("[^ATCGRYSWKMBDHVN]");
		
		// Have to make a deep copy of the primerDict keys, otherwise we get a reference that changes when we change the primerDict
		ArrayList<String> keySet = new ArrayList<String>();
		for(String key : primerDict.keySet()) {
			keySet.add(key);
		}
		for(String key : keySet) {
			String id = key;
			String seq = primerDict.get(id);
			
			// Check for illegal bases
			Matcher matcher = regex.matcher(seq);
			if(matcher.find()) {
				logMessage(outputField, "Primer sequence contains incompatible characters:\n" + id + "\n" + seq);
				return;
			}
			
			// If the sequence contains degenerated bases, create sequences for all possible iterations
			Matcher degenMatcher = degenRegex.matcher(seq);
			if(degenMatcher.find()) {
				ArrayList<String> expandedSeq = expandDegenerated(seq, 0, new ArrayList<String>());
				
				// Remove the original entry which contained degenerate bases, replace with all the possible sequences
				primerDict.remove(key);
				for(int i = 0; i < expandedSeq.size(); i++) {
					String newID = id + "_" + Integer.toString(i);
					String newSeq = expandedSeq.get(i);
					primerDict.put(newID, newSeq);
				}
			}
		}
		
		// Must now write a primer file containing no degenerate bases for the BLAST
		File cleanedPrimers = new File(outDir.getAbsolutePath() + sep + "primer_tmp.fasta");
		try{
			FileWriter writer = new FileWriter(cleanedPrimers);
			for(String key : primerDict.keySet()) {
				writer.write(">" + key + "\n");
				writer.write(primerDict.get(key));
				writer.write("\n");
			}
			writer.close();
		}catch(IOException e) {
			e.printStackTrace();
		}		
	}
	
	// Expand the sequences that contain degenerate bases into every possibility
	public static ArrayList<String> expandDegenerated(String seq, int index, ArrayList<String> primerContainer){		
		
		char[] seqChars = seq.toCharArray();
		// Due to recursive nature, need to keep going from where we left off
		for(int i = index; i < seq.length(); i++) {
			char c = seqChars[i];
			
			// Check if the current character is contained in the list of degenerate bases. If so, replace this instance with each possible base.
			if(degenerates.keySet().contains(c)) {
				for(char s : degenerates.get(c)) {
					String newSeq = seq.replaceFirst(Character.toString(c), Character.toString(s));
					Matcher matcher = degenRegex.matcher(newSeq);
					
					// Check the resulting primers.
					// If more degenerate bases are found, do the same as above, but starting from the base following the one just replaced.
					if(matcher.find()) {
						int j = i + 1;
						ArrayList<String> expanded = expandDegenerated(newSeq, j, primerContainer);
						
					// If no more degenerate bases are found, we have reached the end, and can add the sequence to the list to be returned.
					}else {
						primerContainer.add(newSeq);
					}
				}
			}
		}
		return primerContainer;
	}
	
	// Make a BLAST database from the primers
	public static void makeBlastDB(File reference, File BLASTLocation) {
		String in = reference.getAbsolutePath();
		String options = " -dbtype nucl -parse_seqids -hash_index -in " + in;
		String fullProcessCall = "makeblastdb.exe" + options;
		try{
			Process p = Runtime.getRuntime().exec(BLASTLocation.getAbsolutePath() + "\\" + fullProcessCall);
			try {
				p.waitFor();
			}catch(InterruptedException e) {
				e.printStackTrace();
			}
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	// Add the correct result headers to the Blast tsv output file
	public static void addHeaderToTSV(File tsvFile) {
		String tab = "\t";
		String[] headerFileIDs = new String[] {"qseqid", "sseqid", "positive", "mismatch", "gaps", "evalue",
				"bitscore", "slen", "length", "qstart", "qend", "qseq", "sstart", "send", "sseq"};
		String header = String.join(tab, headerFileIDs);
		
		ArrayList<String> lines = new ArrayList<String>();
		String line;
		try{
			BufferedReader reader = new BufferedReader(new FileReader(tsvFile));
			while((line = reader.readLine()) != null) {
				lines.add(line);
			}
			reader.close();
		}catch(IOException e) {
			e.printStackTrace();
		}
		
		lines.add(0, header);
		try{
			File newFile = new File(tsvFile.getAbsolutePath());
			tsvFile.delete();
			FileWriter writer = new FileWriter(newFile);
			for(String item : lines) {
				writer.write(item + System.getProperty("line.separator"));
			}
			writer.close();
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	// Fills the sampleDict to be used in the consolidated report method
	public static void parseBlastOutput(File consolidatedDir, File detailedReport, HashMap<String, String> primerDict,
			int mismatches, HashMap<String, Sample> sampleDict) {
		
		// List of blast report files
		ArrayList<File> reportList = new ArrayList<File>();
		for(File sampleDir : detailedReport.listFiles()) {
			for(File sample : sampleDir.listFiles()) {
				if(sample.getName().contains(".tsv")) {
					reportList.add(sample);
				}
			}
		}
		
		for(File sampleReport : reportList) {
			String sampleName = sampleReport.getName().split("\\.")[0];
			
			String line;
			try {
				BufferedReader reader = new BufferedReader(new FileReader(sampleReport));
				while((line = reader.readLine()) != null) {
					if(line.equals("") || line.startsWith("qseqid")) {
						continue;
					}
					String[] fields = line.split("\t");
					String qseqid = fields[0];
					String sseqid = fields[1];
					int length = Integer.parseInt(fields[8]);
					int weightedLength = primerDict.get(sseqid).length();
					int actualMismatches = Integer.parseInt(fields[3]);
					
					if(length == weightedLength && actualMismatches <= mismatches) {
						int qstart = Integer.parseInt(fields[9]);
						int qend = Integer.parseInt(fields[10]);
						String sseq = fields[14];
						sampleDict.get(sampleName).addBlastResult(sseqid, new BlastResult(sampleName, qseqid, sseqid, actualMismatches, qstart, qend, length, sseq));
					}
				}
				reader.close();
			}catch(IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	// Makes the final consolidated report from the multiple blast reports
	public static void makeConsolidatedReport(File consolidatedDir, String sep, HashMap<String, Sample> sampleDict) {
		// The header for the consolidated report
		String header = String.join("\t", new String[] {"Sample", "Gene", "GenomeLocation", "AmpliconSize", "Contig", "ForwardPrimers", "ReversePrimers",
				"ForwardMismatches", "ReverseMismatches"}) + "\n";
		
		// Generate the file to be filled in
		File consolidatedReport = new File(consolidatedDir.getAbsolutePath() + sep + "report.tsv");
		try{
			FileWriter writer = new FileWriter(consolidatedReport);
			writer.write(header);
			writer.write(System.getProperty("line.separator"));
			
			for(String key : sampleDict.keySet()) {
				
				// Set up all necessary values
				Sample sample = sampleDict.get(key);
				String sampleName = key;
				HashMap<String, BlastResult> blastResults = sampleDict.get(key).getBlastResults();
				if(!blastResults.isEmpty()) {
					String[] primerHits = blastResults.keySet().toArray(new String[blastResults.keySet().size()]); // Similar to what we did with the degenRegex issue
					HashMap<String, HashMap<String, ArrayList<String>>> primers = new HashMap<String, HashMap<String, ArrayList<String>>>();
					
					/* What this is actually doing is placing the primers into a hashmap based on their base name, alongside
					 *A list of directions. Therefore, a primer set of NAME-F and NAME-R would be listed under NAME with
					 *Directions F and R. Similarly, a degenerate primer of NAME-F_1, NAME-F_2, NAME-R_1, and NAME-R_2 would be
					 *listed under NAME with directions F_1, F_2, R_1, and R_2
					 */
					for(String primer : primerHits) {
						String[] splitPrimer = primer.split("-"); 
						String direction = splitPrimer[splitPrimer.length - 1];
						String primerName = String.join("-", Arrays.copyOfRange(splitPrimer, 0, splitPrimer.length - 1));
						if(!primers.containsKey(primerName)) {
							HashMap<String, ArrayList<String>> list = new HashMap<String, ArrayList<String>>();
							ArrayList<String> fList = new ArrayList<String>();
							ArrayList<String> rList = new ArrayList<String>();
							ArrayList<String> pList = new ArrayList<String>();
							if(direction.startsWith("F")) {
								fList.add(direction);
							}else if(direction.startsWith("R")) {
								rList.add(direction);
							}else if(direction.startsWith("P")) {
								pList.add(direction);
							}
							list.put("F", fList);
							list.put("R", rList);
							list.put("P", pList);
							primers.put(primerName, list);
						}else {
							if(direction.startsWith("F")) {
								primers.get(primerName).get("F").add(direction);
							}else if(direction.startsWith("R")) {
								primers.get(primerName).get("R").add(direction);
							}else if(direction.startsWith("P")) {
								primers.get(primerName).get("P").add(direction);
							}
						}
					}
					
					// Check if primer pairs are present
					for(String primerKey : primers.keySet()) {
						HashMap<String, ArrayList<String>> primersList = primers.get(primerKey);
						if(!primersList.get("F").isEmpty() && !primersList.get("R").isEmpty()) { // Have both F and R primers
							String gene = primerKey;
							for(String fPrimer : primersList.get("F")) {
								for(String rPrimer : primersList.get("R")) {
									String fwdPrimer = gene + "-" + fPrimer;
									String revPrimer = gene + "-" + rPrimer;
									
									// If this pair of primers are not on the same contig, skip and keep going
									if(!sample.getBlastResults().get(fwdPrimer).getQueryID().equals(sample.getBlastResults().get(revPrimer).getQueryID())) {
										continue;
									}
									
									int startF = sample.getBlastResults().get(fwdPrimer).getStart();
									int endF = sample.getBlastResults().get(fwdPrimer).getEnd();
									int startR = sample.getBlastResults().get(revPrimer).getStart();
									int endR = sample.getBlastResults().get(revPrimer).getEnd();
									Integer[] positions = {startF, endF, startR, endR};
									int start = Collections.min(Arrays.asList(positions));
									int end = Collections.max(Arrays.asList(positions));
									String location = Integer.toString(start) + "-" + Integer.toString(end);
									String size = Integer.toString(end - start + 1);
									String contig = sample.getBlastResults().get(gene + "-F").getQueryID();
									String fwdMismatch = Integer.toString(sample.getBlastResults().get(fwdPrimer).getMismatch());
									String revMismatch = Integer.toString(sample.getBlastResults().get(revPrimer).getMismatch());
									
									writer.write(String.join("\t", new String[] {sampleName, gene, location, size, contig, 
											fwdPrimer, revPrimer, fwdMismatch, revMismatch}));
									writer.write(System.getProperty("line.separator"));
									
									// If a qPCR probe exists, 
									if(!primersList.get("P").isEmpty()) {
										for(String pPrimer : primersList.get("P")) {
											String probePrimer = gene + "-" + pPrimer;
											int startP = sample.getBlastResults().get(probePrimer).getStart();
											int endP = sample.getBlastResults().get(probePrimer).getEnd();
											String locationP = Integer.toString(startP) + "-" + Integer.toString(endP);
											String sizeP = Integer.toString(endP - startP + 1);
											String pMismatch = Integer.toString(sample.getBlastResults().get(probePrimer).getMismatch());
											
											// Probe only valid if it is contained within the surrounding amplicon
											if(startP > start && endP < end) {
												writer.write(String.join("\t", new String[] {sampleName + "_probe", gene, locationP, sizeP, contig,
														probePrimer, "N/A", pMismatch, "N/A"}));
												writer.write(System.getProperty("line.separator"));
											}
										}
									}
								}
							}
						}
					}
				}
			}
			writer.close();
		}catch(IOException e) {
			e.printStackTrace();
		}
	}

	// Simple method to print a message to the output TextArea
	public static void logMessage(TextArea outputField, String msg) {
		Platform.runLater(() -> outputField.appendText("\n##########\n" + msg + "\n##########\n"));;
	}
		
}