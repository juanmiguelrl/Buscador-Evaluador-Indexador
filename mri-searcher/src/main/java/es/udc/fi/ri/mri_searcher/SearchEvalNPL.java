package es.udc.fi.ri.mri_searcher;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Properties;


public class SearchEvalNPL {

	private SearchEvalNPL() {
	}


	public static void main(String[] args) throws Exception {
		String usage = "Usage: java es.udc.fi.ri.mri_searcher.SearchEvalNPL -search MODEL_AND_PARAMETER" +
				" [-indexin INDEX_PATH] -cut N -metrica METRIC -top M -queries QUERIES\n\n";

		if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
			System.out.println(usage);
			System.exit(0);
		}

		String searchModel = null;
		String searchStat;
		Similarity similarity = null;
		String index = "index";
		int cut = -1;
		String metrica = null;
		int top = -1;
		String queries = null;
		String field = "Contents";

		for (int i = 0; i < args.length; i++) {
			if ("-search".equals(args[i])) {
				searchModel = args[i + 1];

				if (searchModel.equals("jm")) {
					searchStat = args[i + 2];
					similarity = new LMJelinekMercerSimilarity(Float.parseFloat(searchStat));
				} else if (searchModel.equals("dir")) {
					searchStat = args[i + 2];
					similarity = new LMDirichletSimilarity(Float.parseFloat(searchStat));
				} else if (searchModel.equals("tfidf")) {
					similarity = new ClassicSimilarity();
				}

				i++;

			} else if ("-indexin".equals(args[i])) {
				index = args[i + 1];
				i++;
			} else if ("-cut".equals(args[i])) {
				cut = Integer.parseInt(args[i + 1]);
				i++;
			} else if ("-metrica".equals(args[i])) {
				metrica = args[i + 1];
				i++;
			} else if ("-top".equals(args[i])) {
				top = Integer.parseInt(args[i + 1]);
				i++;
			} else if ("-queries".equals(args[i])) {
				queries = args[i + 1];
				i++;
			}
		}

		if (searchModel == null || cut == -1 || metrica == null || top == -1 || queries == null) {
			System.err.println("Usage: " + usage);
			System.exit(1);
		} else if ( !searchModel.equals("jm") && !searchModel.equals("dir") && !searchModel.equals("tfidf") ) {
			System.err.println("The -search parameter must have one of the following values: {jm lambda | dir mu | tfidf}");
			System.exit(1);
		} else if ( !metrica.equals("P") && !metrica.equals("R") && !metrica.equals("MAP") ) {
			System.err.println("The -metrica parameter must have one of the following values: {P, R, MAP}");
			System.exit(1);
		}

		String[] queries_array;
		int query_int0 = -1;
		int query_int1 = -1;
		int query_int2 = -1;
		if (!queries.equals("all")) {
			if (!queries.contains("-")) {
				query_int0 = Integer.parseInt(queries);
			} else {
				queries_array = queries.split("-");
				query_int1 = Integer.parseInt(queries_array[0]);
				query_int2 = Integer.parseInt(queries_array[1]);
			}
		}



		IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
		IndexSearcher searcher = new IndexSearcher(reader);
		searcher.setSimilarity(similarity);
		System.out.println("Searching similarity = " + similarity.toString());

		Analyzer analyzer = new StandardAnalyzer();


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

		// Comprobamos que los valores indicados en config.properties sean correctos
		if (docs == null) {
			System.err.println("Debe indicarse el DOCS_PATH en la variable 'docs' del archivo config.properties");
			System.exit(1);
		}

		// Obtenemos el path del archivo doc-text
		//Path querytext_path = Paths.get(docs, "query-text");

		//BufferedReader in = null;
		BufferedReader in = Files.newBufferedReader(Paths.get(docs, "query-text"), StandardCharsets.UTF_8);
		QueryParser parser = new QueryParser(field, analyzer);
		//parser.setDefaultOperator(QueryParser.Operator.OR);

		BufferedReader in2 = Files.newBufferedReader(Paths.get(docs, "rlv-ass"), StandardCharsets.UTF_8);



		TopDocs results = null;
		int position = -1;
		String queryContent = "";
		String relevantContent = "";
		String line = "";
		String line2 = "";
		line = in.readLine(); //numero
		line2 = in2.readLine(); //numero
		int i;
		ArrayList<Float> listaMetricas = new ArrayList<>();

		int max = Math.max(cut, top);

		while ((line != null) && (!line.equals(""))) {		// Para cada query
			queryContent = "";
			relevantContent = "";
			//pasa a nº la linea
			position = Integer.parseInt(line);
			line = in.readLine(); // query
			line2 = in2.readLine(); // lista relevantes
			if ((queries.equals("all")) ||
					(query_int0 == position) ||
					((query_int1 <= position) && (query_int2 >= position))) {
				//queryContent = line;
				//n++;
				//System.out.println(line);
				//line = in.readLine();
				while ((!line.equals("/")) && (line != null)) {
					queryContent += " " + line;
					line = in.readLine(); // lee una cuando acaba barra
					//System.out.println(line);
				}


				while ((!line2.equals("   /")) && (line2 != null)) {
					relevantContent += " " + line2;
					line2 = in2.readLine(); // lee una cuando acaba barra
					//System.out.println(line);
				}
				ArrayList<Integer> listaRelevantes = new ArrayList<>();
					String[] relevantArray = relevantContent.split("\\s+");
					for (i = 0; i < relevantArray.length; i++) {
						if (!relevantArray[i].equals("")) {
							//System.out.println(relevantArray[i]);
							listaRelevantes.add(Integer.parseInt(relevantArray[i]));
						}
					}
				//System.out.println("lista de relevantes" + listaRelevantes.toString
				//queryContent = queryContent.trim();
				queryContent = queryContent.toLowerCase();
				System.out.println("\nSearching for: " + field + "\n query nº " + position + ": " + queryContent);
				Query query = parser.parse(queryContent);
				results = searcher.search(query, max);
				//System.out.println(results.scoreDocs.toString());
				line = in.readLine(); // numero
				line2 = in2.readLine();
				//results.scoreDocs

				// Imprimimos por pantalla los top M documentos del ranking
				int limit = Math.min(results.scoreDocs.length, top);
				int k;
				String relevancia = null;

				for (i = 0; i < limit; i++) {
					ScoreDoc result = results.scoreDocs[i];
					Document documento = searcher.doc(result.doc);
					int docIDNPL = Integer.parseInt(documento.get("DocIDNPL"));
					String contenido = documento.get("Contents");
					k = i + 1;

					if (listaRelevantes.contains(docIDNPL)) {
						relevancia = "SI";
					} else {
						relevancia = "NO";
					}
					System.out.println("Result #" + k + ":\t\tscore=" + result.score + "\t\trel=" + relevancia);
					System.out.println("Campo 'DocIDNPL':\n" + docIDNPL);
					System.out.println("Campo 'Contents':\n" + contenido);

				}

				// Añadimos los top N documentos del ranking a la lista listaRecuperados
				ArrayList<Integer> listaRecuperados = new ArrayList<>();
				limit = Math.min(results.scoreDocs.length, cut);
				for (i = 0; i < limit; i++) {
					ScoreDoc result = results.scoreDocs[i];
					//listaRecuperados.add(result.doc);
					Document documento = searcher.doc(result.doc);
					int docIDNPL = Integer.parseInt(documento.get("DocIDNPL"));
					listaRecuperados.add(docIDNPL);

				}

				//System.out.println("listaRelevantes = " + listaRelevantes.toString());
				//System.out.println("listaRecuperados = " + listaRecuperados.toString());


				// Calculamos la métrica para cada query y la guardamos en listaMetricas
				Float aux;
				if (listaRelevantes.size() > 0) {
					if (metrica.equals("P")) {
						aux = calculatePrecision(listaRecuperados, listaRelevantes);
						listaMetricas.add(aux);
						System.out.println("Precision: " + aux);

					} else if (metrica.equals("R")) {
						aux = calculateRecall(listaRecuperados, listaRelevantes);
						listaMetricas.add(aux);
						System.out.println("Recall: " + aux);

					} else if (metrica.equals("MAP")) {
						aux = calculateAveragePrecision(listaRecuperados, listaRelevantes);
						listaMetricas.add(aux);
						System.out.println("Average Precision: " + aux);
					}

				}
				System.out.println("----------------------------------------------------------------------------");
			}

			else {
				while ((!line.equals("/")) && (line != null)) {
					//queryContent += line;
					line = in.readLine(); // lee una cuando acaba barra
					//System.out.println(line);
				}
				while ((!line2.equals("   /")) && (line2 != null)) {
					//relevantContent += " " + line2;
					line2 = in2.readLine(); // lee una cuando acaba barra
					//System.out.println(line);
				}

				line = in.readLine(); // numero
				line2 = in2.readLine();


			}
		}

		// Imprimimos listaMetricas por pantalla
		String metrica_nombre = "?";
		if (metrica.equals("P")) {
			metrica_nombre = "Precision";
		} else if (metrica.equals("R")) {
			metrica_nombre = "Recall";
		} else if (metrica.equals("MAP")) {
			metrica_nombre = "Average precision";
		}

		System.out.println();
		System.out.println("Metrica: " + metrica_nombre);
		System.out.println("listaMetricas = " + listaMetricas.toString());


		// Calculamos la media de las métricas
		if (listaMetricas.size() == 0) {
			System.out.println("No se pudo calcular el promedio de la métrica. Division por 0");
			System.exit(0);
		}

		float sum = 0;
		float mean;
		for (Float value : listaMetricas) {
			sum += value;
		}
		mean = sum / listaMetricas.size();

		// Imprimimos la media de las métricas por pantalla
		System.out.println("Media de " + metrica_nombre + ": " + mean);


		reader.close();
	}


	private static float calculatePrecision(ArrayList<Integer> listaRecuperados, ArrayList<Integer> listaRelevantes) {

		int relevantesRecuperados = 0;
		int recuperados = listaRecuperados.size();

		for (Integer elem: listaRecuperados) {
			if (listaRelevantes.contains(elem)) {
				relevantesRecuperados++;
				//System.out.println("--DEBUG-- docIDNPL que es relevantesRecuperados: " + elem);
			}
		}
		return ((float) relevantesRecuperados / (float) recuperados);

	}

	private static float calculateRecall(ArrayList<Integer> listaRecuperados, ArrayList<Integer> listaRelevantes) {

		int relevantesRecuperados = 0;
		int relevantes = listaRelevantes.size();
		if (relevantes > 0) {
			for (Integer elem: listaRecuperados) {
				if (listaRelevantes.contains(elem)) {
					relevantesRecuperados++;
				}
			}
			return ((float) relevantesRecuperados/ (float) relevantes);
		}
		else {
			return 1;
		}
	}

	private static float calculateAveragePrecision(ArrayList<Integer> listaRecuperados, ArrayList<Integer> listaRelevantes) {
		float precision = 0;
		int relevantesRecuperados = 0;
		int recuperados = 0;
		int relevantes = listaRelevantes.size();

		for (Integer elem: listaRecuperados) {
			recuperados++;
			if (listaRelevantes.contains(elem)) {
				relevantesRecuperados++;
				precision += ((float) relevantesRecuperados / (float) recuperados);
			}
		}
		return (precision / (float) relevantes);
	}

}
