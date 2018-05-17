
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.jruby.embed.LocalVariableBehavior;
import org.jruby.embed.PathType;
import org.jruby.embed.ScriptingContainer;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import org.apache.tika.language.LanguageIdentifier;

//This program parses json files generated by Science-Parse and exports pre-cleaned single-sentence-text-files usable by JStylo.

public class Run {
	public static void main(String[] args) throws IOException {

		// location of json file
		String json_file = "/home/name/desktop/test/example.json";

		// directory of needed resources
		String resources_directory = "/home/name/desktop/test/resources/";

		// directory for output
		String output_directory = "/home/name/desktop/test/output/";

		ArrayList<String> list_of_sentences = new ArrayList<String>();
		
		// get sections
		Gson g = new Gson();
		JsonReader reader = new JsonReader(new FileReader(json_file));
		Document document = g.fromJson(reader, Document.class);
		List<Section> sections = document.getMetadata().getSections();
		if (sections != null)
			sections.removeIf(Objects::isNull);
		if (sections != null && !sections.isEmpty()) {
			for (Section section : sections) {
				String currentSection = section.getText();
				BufferedReader br = new BufferedReader(new InputStreamReader(
						new FileInputStream(resources_directory + "german-abbreviations-utf8.txt"), "UTF-8"));
				String line;
				
				//replace periods in abbreviations by whitespaces
				while ((line = br.readLine()) != null) {
					String repl = line.replaceAll("\\.", " ");
					String maskedLine = line.replace(".", "\\.");
					currentSection = currentSection.replaceAll("\\s" + maskedLine + "\\s", " " + repl + " ");
				}
				br.close();
				
				//remove enumerations
				currentSection = currentSection.replaceAll("\\d\\.", "");
				//harmonize brackets
				currentSection = currentSection.replace("[", "(");
				currentSection = currentSection.replace("]", ")");
				
				//remove brackets and brackets-content 
				currentSection = currentSection.replaceAll("\\(.+?\\)", " ");
				
				//remove all characters, except letters, white spaces and end-of-sentence symbols. 
				currentSection = currentSection.replaceAll("[^A-ZÄÖÜa-zäöüß\\s\\.\\?!]", "");
				
				//harmonize periods I
				currentSection = currentSection.replace(".", ". ");
				
				//remove double whitespaces
				while (Pattern.compile("\\s\\s").matcher(currentSection).find()) {
					currentSection = currentSection.replaceAll("\\s\\s", " ");
				}
				
				//harmonize periods II
				currentSection = currentSection.replace(" .", ".");
				while (Pattern.compile("\\.\\.").matcher(currentSection).find()) {
					currentSection = currentSection.replaceAll("\\.\\.", ".");
				}

				//run sentence boundry detection (by pragmatic segmenter) in jruby container
				ScriptingContainer ruby = new ScriptingContainer(LocalVariableBehavior.PERSISTENT);
				ruby.put("text", currentSection);
				Object result = ruby.runScriptlet(PathType.ABSOLUTE, resources_directory + "sbt.rb");
				String sentence = result.toString();
				
				//parse output of pragmatic segmenter
				sentence = sentence.replace("[\"", "");
				sentence = sentence.replace("\"]", "");
				String[] sentences = sentence.split("\", \"");

				//additional filtering for sentence-defining criteria
				for (String string : sentences) {
					if (Pattern.compile("^[A-ZÄÖÜ][a-zäöüß]+").matcher(string.trim()).find()
							&& Pattern.compile("[\\.\\?!]$").matcher(string.trim()).find()) {
						list_of_sentences.add(string);
					}
				}
			}
		}
		
		//filter non-german sentences and create a text document for each sentence
		for (int i = 0; i < list_of_sentences.size(); i++) {
			LanguageIdentifier identifier = new LanguageIdentifier(list_of_sentences.get(i));
			String language = identifier.getLanguage();
			if (language.equalsIgnoreCase("de")) {
				FileWriter writer = new FileWriter(output_directory + (i + 1) + ".txt");
				writer.write(list_of_sentences.get(i));
				writer.close();
			}
		}

	}
}
