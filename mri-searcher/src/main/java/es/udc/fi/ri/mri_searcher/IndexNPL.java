package es.udc.fi.ri.mri_searcher;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Properties;


public class IndexNPL {

	private static IndexWriterConfig.OpenMode openmode = OpenMode.CREATE;
	
	/* Not Indexed, not tokenized, stored. */
	private static final FieldType TYPE_NUMBER = new FieldType();
	private static final FieldType TYPE_TEXTFIELD = new FieldType();
	private static final IndexOptions options_NUMBER = IndexOptions.DOCS_AND_FREQS;
	private static final IndexOptions options_TEXTFIELD = IndexOptions.DOCS_AND_FREQS;
	//private static final IndexOptions options = IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS;

	static {
		TYPE_NUMBER.setIndexOptions(options_NUMBER);
		TYPE_NUMBER.setTokenized(false);
		TYPE_NUMBER.setStored(true);
		TYPE_NUMBER.setStoreTermVectors(true);
		TYPE_NUMBER.setStoreTermVectorPositions(false);
		TYPE_NUMBER.freeze();
	}
	
	static {
		TYPE_TEXTFIELD.setIndexOptions(options_TEXTFIELD);
		TYPE_TEXTFIELD.setTokenized(true);
		TYPE_TEXTFIELD.setStored(true);		// Cambiado a STORED para usarlo en ManualRelevanceFeedbackNPL
		TYPE_TEXTFIELD.setStoreTermVectors(true);
		TYPE_TEXTFIELD.setStoreTermVectorPositions(false);
		TYPE_TEXTFIELD.freeze();
	}


	private IndexNPL() {
	}


	public static void main(String[] args) {
		String usage = "Usage: java es.udc.fi.ri.mri_searcher.IndexNPL [-index INDEX_PATH] [-openmode OPENMODE]\n\n";
		
		String indexPath = "index";
		String openmode_str = null;
		
		for (int i = 0; i < args.length; i++) {
			if ("-index".equals(args[i])) {
				indexPath = args[i + 1];
				i++;
			} else if ("-openmode".equals(args[i])) {
				openmode_str = args[i + 1];
				i++;

				switch (openmode_str) {
			        case "append":
			            openmode = OpenMode.APPEND;
			            break;
			        case "create":
			            openmode = OpenMode.CREATE;
			            break;
			        case "create_or_append":
			            openmode = OpenMode.CREATE_OR_APPEND;
			            break;
			    }

			}
		}

		// Comprobamos que openmode sea correcto
		if (openmode_str != null && !openmode_str.equals("append") && !openmode_str.equals("create") &&
				!openmode_str.equals("create_or_append") ) {
			System.err.println("The -openmode parameter must have one of the following values: {append, create, create_or_append}");
			System.exit(1);
		}

    	// Leer el archivo config.properties
    	Properties properties = new Properties();
    	try {
    		properties.load(new FileInputStream(new File("src\\main\\resources\\config.properties")));
    	} catch (FileNotFoundException e) {
    		e.printStackTrace();
    	} catch (IOException e) {
    		e.printStackTrace();
    	}
    	
    	String docs = properties.get("docs").toString();
    	String indexingmodel = properties.get("indexingmodel").toString();
    	
    	// Comprobamos que los valores indicados en config.properties sean correctos
		if (docs == null) {
			System.err.println("Debe indicarse el DOCS_PATH en la variable 'docs' del archivo config.properties");
			System.exit(1);
		}
		if (indexingmodel == null) {
			System.err.println("Debe indicarse el INDEXING_MODEL en la variable 'indexingmodel' del archivo config.properties");
			System.exit(1);
		}

		// Determinamos que similarity vamos a usar
		// indexingmodel values: {jm lambda | dir mu | tfidf}
		Similarity similarity = null;
		Float param;

		if (indexingmodel.startsWith("jm")) {
			param = Float.parseFloat(indexingmodel.split(" ")[1]);
			similarity = new LMJelinekMercerSimilarity(param);

		} else if (indexingmodel.startsWith("dir")) {
			param = Float.parseFloat(indexingmodel.split(" ")[1]);
			similarity = new LMDirichletSimilarity(param);

		} else if (indexingmodel.equals("tfidf")) {
			// TFIDFSimilarity es una clase abstracta. ClassicSimilarity extiende TFIDFSimilarity
			similarity = new ClassicSimilarity();

		} else {
			System.err.println("The parameter 'indexingmodel' of 'config.properties' file must have one of the following values: {jm lambda | dir mu | tfidf}");
			System.exit(1);
		}



		Date start = new Date();
		try {

			Directory dir = null;
			Analyzer analyzer = new StandardAnalyzer();

			// Ajustes indexwriter
			IndexWriterConfig iwc = null;
			iwc = new IndexWriterConfig(analyzer);
			iwc.setSimilarity(similarity);
			iwc.setOpenMode(openmode);
			System.out.println("Indexing similarity = " + similarity.toString());

			// Leer config.properties
			//System.out.println("Indice a crear = " + indexPath);
			dir = FSDirectory.open(Paths.get(indexPath));

			IndexWriter writerFinal = new IndexWriter(dir, iwc);


			// Obtenemos el path del archivo doc-text
			Path doctext_path = Paths.get(docs, "doc-text");

			if (!Files.isReadable(doctext_path)) {
				System.out.println("Document directory '" + doctext_path.toAbsolutePath()
						+ "' does not exist or is not readable, please check the path");
				System.exit(1);
			}

			// Comenzamos la indexación
			System.out.println("Indexing to directory '" + indexPath + "'...");
			indexDocs(writerFinal, doctext_path);

			// Cerramos todos los writers
			writerFinal.commit();
			writerFinal.close();


			Date end = new Date();
			System.out.println(end.getTime() - start.getTime() + " total milliseconds");

		} catch (IOException e) {
			System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
		}
	}


	// Parsea e indexa el archivo file (doc-text de la colección NPL)
	static void indexDocs(final IndexWriter writer, Path file) throws IOException {

		String finalContent = "";

		try (InputStream stream = Files.newInputStream(file)) {
			Document doc = null;

			//doc.add(new Field("Contents", new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)), TYPE_TEXTFIELD));
			BufferedReader buffer = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			String line = "";
			line = buffer.readLine();
			int n = 0;
			while ((line != null) && (!line.equals(""))) {
				finalContent = "";
				// Crear un nuevo documento Lucene
				doc = new Document();
				//pasar linea a nº y guardarlo como el DocIDNPL
				doc.add(new Field("DocIDNPL", line, TYPE_NUMBER));
				n++;
				//System.out.println(line);
				line = buffer.readLine();
				while ((!line.equals("   /")) && (line != null)) {
					finalContent += line + "\n";
					line = buffer.readLine();
					//System.out.println(line);
				}
				//System.out.println(line);
				doc.add(new Field("Contents", finalContent, TYPE_TEXTFIELD));


				boolean index_exists = DirectoryReader.indexExists(writer.getDirectory());

				if (openmode == OpenMode.CREATE) {
					//System.out.println("adding " + file);
					writer.addDocument(doc);

				} else if (openmode == OpenMode.APPEND) {
					//System.out.println("adding " + file);
					writer.addDocument(doc);

				} else if (openmode == OpenMode.CREATE_OR_APPEND && !index_exists) {
					//System.out.println("adding " + file);
					writer.addDocument(doc);

				} else if (openmode == OpenMode.CREATE_OR_APPEND && index_exists) {
					//System.out.println("adding " + file);
					writer.addDocument(doc);
				}

				line = buffer.readLine();
			}
			System.out.println("Number of indexed documents: " + n);

		}
	}
}
