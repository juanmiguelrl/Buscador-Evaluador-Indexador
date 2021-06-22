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
import java.util.List;
import java.util.Properties;


public class TrainingTestNPL {

	private TrainingTestNPL() {
	}


	public static void main(String[] args) throws Exception {
		String usage = "Usage: java es.udc.fi.ri.mri_searcher.TrainingTestNPL -evaljm int1-int2 int3-int4" +
				" [-indexin INDEX_PATH] -cut N -metrica METRIC -outfile OUTPUT_PATH\n" +
				"Usage: java es.udc.fi.ri.mri_searcher.TrainingTestNPL -evaldir int1-int2 int3-int4" +
				" [-indexin INDEX_PATH] -cut N -metrica METRIC -outfile OUTPUT_PATH\n\n";

		if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
			System.out.println(usage);
			System.exit(0);
		}

		boolean evaljm = false;
		boolean evaldir = false;

		String searchModel = null;
		String searchStat;
		Similarity similarity = null;
		String index = "index";
		int cut = -1;
		String cutStr = null;
		String metrica = null;
		int top = -1;
		String queries = null;
		String outfile = null;

		String rango1 = null;		// int1-int2
		String rango2 = null;		// int3-int4
		String[] queries_array;
		int query_int1 = -1;
		int query_int2 = -1;
		int query_int3 = -1;
		int query_int4 = -1;
		//float[] arrayjm = {0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0};

		String parameter_str = "?";

		for (int i = 0; i < args.length; i++) {
			if ("-evaljm".equals(args[i])) {
				evaljm = true;
				searchModel = "jm";

				rango1 = args[i + 1];
				rango2 = args[i + 2];

				queries_array = args[i + 1].split("-");
				query_int1 = Integer.parseInt(queries_array[0]);
				query_int2 = Integer.parseInt(queries_array[1]);

				queries_array = args[i + 2].split("-");
				query_int3 = Integer.parseInt(queries_array[0]);
				query_int4 = Integer.parseInt(queries_array[1]);

				i++;

			} if ("-evaldir".equals(args[i])) {
				evaldir = true;
				searchModel = "dir";

				rango1 = args[i + 1];
				rango2 = args[i + 2];

				queries_array = args[i + 1].split("-");
				query_int1 = Integer.parseInt(queries_array[0]);
				query_int2 = Integer.parseInt(queries_array[1]);

				queries_array = args[i + 2].split("-");
				query_int3 = Integer.parseInt(queries_array[0]);
				query_int4 = Integer.parseInt(queries_array[1]);

				i++;

			} else if ("-indexin".equals(args[i])) {
				index = args[i + 1];
				i++;
			} else if ("-cut".equals(args[i])) {
				//cut = Integer.parseInt(args[i + 1]);
				cutStr = args[i + 1];
				i++;
			} else if ("-metrica".equals(args[i])) {
				metrica = args[i + 1];
				i++;
			} else if ("-outfile".equals(args[i])) {
				outfile = args[i + 1];
				i++;
			}
		}

		if (searchModel == null || cutStr == null || metrica == null || outfile == null) {
			System.err.println("Usage: " + usage);
			System.exit(1);
		} else if (!searchModel.equals("jm") && !searchModel.equals("dir") && !searchModel.equals("tfidf")) {
			System.err.println("The -search parameter must have one of the following values: {jm lambda | dir mu | tfidf}");
			System.exit(1);
		} else if (!metrica.equals("P") && !metrica.equals("R") && !metrica.equals("MAP")) {
			System.err.println("The -metrica parameter must have one of the following values: {P, R, MAP}");
			System.exit(1);
		} else if (evaljm && evaldir) {
			System.err.println("The -evaljm and -evaldir parameters are exclusive options");
			System.exit(1);
		}


		System.out.println("Evaluando con searchModel = " + searchModel);
		System.out.println();

		// Llamamos a la función Search()
		ArrayList<ArrayList<Float>> tabla = new ArrayList<>();
		ArrayList<Float> listaEval = new ArrayList<>();
		float bestMetric = -1;
		if (evaljm) {
			float best = 0;
			float actual;
			bestMetric = -1;
			for (float f = 1; f <= 10; f += 1) {
				float aux = f / 10;
				String[] input = {"-search", "jm", Float.toString(aux), "-indexin", index, "-cut", cutStr, "-top", "0", "-metrica", metrica, "-queries", rango1};
				ArrayList<Float> listaColumna = Search(input);
				tabla.add(listaColumna);
				actual = listaColumna.get(listaColumna.size() - 1);
				if (best < actual) {
					best = actual;
					bestMetric = aux;
				}
			}
			String[] input = {"-search", "jm", Float.toString(bestMetric), "-indexin", index, "-cut", cutStr, "-top", "0", "-metrica", metrica, "-queries", rango2};
			listaEval = Search(input);
		}
		else if (evaldir) {
			float best = 0;
			float actual;
			bestMetric = -1;
			for (int i = 0; i <= 5000; i+=500) {
				String[] input = { "-search","dir", Integer.toString(i), "-indexin", index, "-cut", cutStr, "-top", "0", "-metrica", metrica, "-queries", rango1 };
				ArrayList<Float> listaColumna = Search( input );
				tabla.add(listaColumna);
				actual = listaColumna.get(listaColumna.size()-1);
				if ( best < actual) {
					best = actual;
					bestMetric = i;
				}
			}
			String[] input = {"-search", "dir", Float.toString(bestMetric), "-indexin", index, "-cut", cutStr, "-top", "0", "-metrica", metrica, "-queries", rango2};
			listaEval = Search(input);
		}

		// Imprimimos las tablas por pantalla

		if (evaljm) {
			parameter_str = "lambda";
			System.out.print("\t\t\t\t   ");

			for (float f = 1; f <= 10; f+= 1) {
				float aux = f / 10;
				System.out.print(aux + "\t\t   ");
			}

			int rango = query_int2 - query_int1;
			int i;
			for (i= 0;i <= rango; i++) {
				System.out.println();
				System.out.print("query " + query_int1 + "\t\t");
				query_int1++;
				for (ArrayList<Float> colum : tabla) {
					System.out.printf("%.4f\t\t", colum.get(i));
					//System.out.print(colum.get(i) + "\t");
				}

			}
			System.out.println();
			System.out.printf("mean\t\t\t");
			for (ArrayList<Float> colum : tabla) {
				System.out.printf("%.4f\t\t", colum.get(i));
				//System.out.print(colum.get(i) + "\t");
			}

			System.out.println();
		}

		else if (evaldir) {
			parameter_str = "mu";
			System.out.print("\t\t\t\t");

			for (int i = 0; i <= 5000; i+=500) {
				System.out.print(i + "\t\t   ");
			}

			int rango = query_int2 - query_int1;
			int i;
			for (i= 0;i <= rango; i++) {
				System.out.println();
				System.out.print("query " + query_int1 + "\t\t");
				query_int1++;
				for (ArrayList<Float> colum : tabla) {
					System.out.printf("%.4f\t\t", colum.get(i));
					//System.out.print(colum.get(i) + "\t");
				}

			}
			System.out.println();
			System.out.printf("mean\t\t\t");
			for (ArrayList<Float> colum : tabla) {
				System.out.printf("%.4f\t\t", colum.get(i));
				//System.out.print(colum.get(i) + "\t");
			}

			System.out.println();
		}

		int i = 0;
		System.out.println();
		System.out.println("Mejor valor del parámetro " + parameter_str + ": " + bestMetric);
		System.out.println();
		System.out.println("Resultados del parámetro óptimo (" + parameter_str + " = " + bestMetric + ") para las queries de test");
		int rango = (query_int4 - query_int3);

		// Creamos el archivo con los resultados
		Writer fileWriter = new FileWriter(outfile, false);

		for (i = 0; i <= rango; i++) {
			//System.out.printf("%.4f\t\t", colum);
			System.out.printf("query %s\t\t%.4f\n", query_int3, listaEval.get(i));
			fileWriter.write(query_int3 + ", " + listaEval.get(i) + "\n");
			query_int3++;
		}
		System.out.printf("mean " + "\t\t\t%.4f\n", listaEval.get(i) );

		fileWriter.close();

		System.out.println();
		System.out.println("Creado archivo '" + outfile + "'");

	}



	public static ArrayList<Float> Search(String[] args) throws Exception {
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
		//System.out.println("Searching similarity = " + similarity.toString());

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
				//System.out.println("lista de relevantes" + listaRelevantes.toString());

				//System.out.println("\nSearching for: " + field + "\n query nº " + position + ": " + queryContent);
				//queryContent = queryContent.trim();
				queryContent = queryContent.toLowerCase();
				Query query = parser.parse(queryContent);
				results = searcher.search(query, max);
				//System.out.println(results.scoreDocs.toString());
				line = in.readLine(); // numero
				line2 = in2.readLine();
				//results.scoreDocs

				// Imprimimos por pantalla los top M documentos del ranking
				int limit = Math.min(results.scoreDocs.length, top);
				int k;
				for (i = 0; i < limit; i++) {
					ScoreDoc result = results.scoreDocs[i];
					Document documento = searcher.doc(result.doc);
					int docIDNPL = Integer.parseInt(documento.get("DocIDNPL"));
					k = i + 1;
					//System.out.println("Result #" + k + ":\t\tdoc=" + docIDNPL + " score=" + result.score);
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
				//if (listaRelevantes.size() > 0) {
					if (metrica.equals("P")) {
						aux = calculatePrecision(listaRecuperados, listaRelevantes);
						listaMetricas.add(aux);

					} else if (metrica.equals("R")) {
						aux = calculateRecall(listaRecuperados, listaRelevantes);
						listaMetricas.add(aux);

					} else if (metrica.equals("MAP")) {
						aux = calculateAveragePrecision(listaRecuperados, listaRelevantes);
						listaMetricas.add(aux);
					}
				//}
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

		//System.out.println();
		//System.out.println("Metrica: " + metrica_nombre);
		//System.out.println("listaMetricas = " + listaMetricas.toString());


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
		//System.out.println("Media de " + metrica_nombre + ": " + mean);


		reader.close();

		listaMetricas.add(mean);
		return listaMetricas;
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
