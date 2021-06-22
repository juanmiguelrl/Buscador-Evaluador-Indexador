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
import java.util.Scanner;

public class ManualRelevanceFeedbackNPL {


    public static void main(String[] args) throws Exception {
        String usage = "Usage: java es.udc.fi.ri.mri_searcher.ManualRelevanceFeedbackNPL -retmodel MODEL_AND_PARAMETER" +
                " [-indexin INDEX_PATH] -cut N -metrica METRIC [-residual T_F] -query QUERY\n\n";

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
        String queries = null;
        String field = "Contents";
        String residualStr = "?";
        boolean residual = false;
        int queryN = -1;
        boolean continuar = true;

        for (int i = 0; i < args.length; i++) {
            if ("-retmodel".equals(args[i])) {
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
            } else if ("-residual".equals(args[i])) {
                residualStr = args[i + 1];
                if (residualStr.equals("T")) {
                    residual = true;
                } else if (residualStr.equals("F")) {
                    residual = false;
                }
                i++;
            } else if ("-query".equals(args[i])) {
                queryN = Integer.parseInt(args[i + 1]);
                i++;
            }
        }

        if (searchModel == null || cut == -1 || metrica == null || queryN == -1) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        } else if ( !searchModel.equals("jm") && !searchModel.equals("dir") && !searchModel.equals("tfidf") ) {
            System.err.println("The -retmodel parameter must have one of the following values: {jm lambda | dir mu | tfidf}");
            System.exit(1);
        } else if ( !metrica.equals("P") && !metrica.equals("R") && !metrica.equals("MAP") ) {
            System.err.println("The -metrica parameter must have one of the following values: {P, R, MAP}");
            System.exit(1);
        } else if ( !residualStr.equals("?") && !residualStr.equals("T") && !residualStr.equals("F") ) {
            System.err.println("The -residual parameter must have one of the following values: {T, F}");
            System.exit(1);
        }

        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        System.out.println("Searching similarity:\tsimilarity = " + similarity.toString());
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

        //BufferedReader in = null;
        BufferedReader in = Files.newBufferedReader(Paths.get(docs, "query-text"), StandardCharsets.UTF_8);
        QueryParser parser = new QueryParser(field, analyzer);
        //parser.setDefaultOperator(QueryParser.Operator.OR);
        BufferedReader in2 = Files.newBufferedReader(Paths.get(docs, "rlv-ass"), StandardCharsets.UTF_8);


        TopDocs results = null;
        int position = -1;
        String relevantContent = "";
        String line = "";
        String line2 = "";
        int i;
        int max = cut;

        //-----------------------------------------------------------------------------------------

        String queryContent = "";
        ArrayList<Integer> listaRelevantes = new ArrayList<>();

        // Recorremos los archivos query-text y rlv-ass para obtener queryContent y listaRelevantes

        line = in.readLine(); //numero
        line2 = in2.readLine(); //numero

        while ((line != null) && (!line.equals(""))) {
            relevantContent = "";
            //pasa a nº la linea
            position = Integer.parseInt(line);
            line = in.readLine(); // query
            line2 = in2.readLine(); // lista relevantes

            if (queryN == position) {
                while ((!line.equals("/")) && (line != null)) {
                    queryContent += " " + line;
                    line = in.readLine(); // lee una cuando acaba barra
                    //System.out.println(line);
                }
                queryContent = queryContent.trim();

                while ((!line2.equals("   /")) && (line2 != null)) {
                    relevantContent += " " + line2;
                    line2 = in2.readLine(); // lee una cuando acaba barra
                    //System.out.println(line);
                }
                relevantContent = relevantContent.trim();

                String[] relevantArray = relevantContent.split("\\s+");
                for (i = 0; i < relevantArray.length; i++) {
                    if (!relevantArray[i].equals("")) {
                        //System.out.println(relevantArray[i]);
                        listaRelevantes.add(Integer.parseInt(relevantArray[i]));
                    }
                }

                //line = in.readLine(); // numero
                //line2 = in2.readLine(); // numero

                break;

            } else {
                while ((!line.equals("/")) && (line != null)) {
                    line = in.readLine(); // lee una cuando acaba barra
                    //System.out.println(line);
                }
                while ((!line2.equals("   /")) && (line2 != null)) {
                    line2 = in2.readLine(); // lee una cuando acaba barra
                    //System.out.println(line);
                }

                line = in.readLine(); // numero
                line2 = in2.readLine(); // numero
            }
        }

        System.out.println("Original query:\t\t\tqueryN = " + queryN);
        System.out.println();

        //-----------------------------------------------------------------------------------------

        // Bucle para cada query a ejecutar (query original + queries introducidas por usuario)
        while (continuar) {
            queryContent = queryContent.toLowerCase();
            Query query = parser.parse(queryContent);
            results = searcher.search(query, max);
            //System.out.println(results.scoreDocs.toString());

            int limit = Math.min(results.scoreDocs.length, cut);
            ArrayList<Integer> listaRecuperados = new ArrayList<>();
            boolean relevant = false;
            boolean relevantFound = false;
            int docID_aux = -1;

            // Información del primer documento relevante
            int rankPosition = -1;
            float score = -1;
            int docID = -1;
            String docContent = null;

            for (i = 0; i < limit; i++) {
                ScoreDoc result = results.scoreDocs[i];
                Document documento = searcher.doc(result.doc);
                docID_aux = Integer.parseInt(documento.get("DocIDNPL"));
                listaRecuperados.add(docID_aux);

                relevant = listaRelevantes.contains(docID_aux);

                if (relevant && !relevantFound) {   // Para el primer documento relevante
                    relevantFound = true;
                    rankPosition = i + 1;
                    score = result.score;
                    docID = Integer.parseInt(documento.get("DocIDNPL"));
                    docContent = documento.get("Contents");
                }
            }

            // Calculamos el valor de la métrica para la query
            String metricName = null;
            float metricValue = -1;

            if (listaRelevantes.size() > 0) {
                if (metrica.equals("P")) {
                    metricName = "Precision";
                    metricValue = calculatePrecision(listaRecuperados, listaRelevantes);
                } else if (metrica.equals("R")) {
                    metricName = "Recall";
                    metricValue = calculateRecall(listaRecuperados, listaRelevantes);
                } else if (metrica.equals("MAP")) {
                    metricName = "Average precision";
                    metricValue = calculateAveragePrecision(listaRecuperados, listaRelevantes);
                }
            } else {
                System.out.println("Error: No queda ningún documento relevante");
                System.exit(0);
            }

            // Prints para debug
            System.out.println("listaRelevantes = " + listaRelevantes.toString());
            System.out.println("listaRecuperados = " + listaRecuperados.toString());

            // Imprimimos toda la información por pantalla
            System.out.println("QUERY: " + queryContent);
            if (relevantFound) {
                System.out.println("MÉTRICA: " + metricName + " = " + metricValue);
                System.out.println("PRIMER DOCUMENTO RELEVANTE DEL RANKING:");
                System.out.println("Posición en el ranking: " + rankPosition);
                System.out.println("Score: " + score);
                System.out.println("Contenido del campo 'DocIDNPL':\n" + docID);
                System.out.print("Contenido del campo 'Contents':\n" + docContent);
            } else {
                System.out.println("No se ha encontrado ningún documento relevante entre los " + cut + " primeros del ranking");
            }


            // Si tenemos activada la opción residual, eliminamos el documento de listaRelevantes
            if (residual) {
                listaRelevantes.remove(Integer.valueOf(docID));
                System.out.println("listaRelevantes.remove(" + docID + ")");
                System.out.println("listaRelevantes = " + listaRelevantes.toString());
            }


            // Obtenemos input del usuario
            System.out.println();
            Scanner console = new Scanner(System.in);
            while (true) {
                System.out.print("¿Desea formular otra query? [s/n] ");
                String continuarStr = console.nextLine();
                if (continuarStr.equals("n")) {
                    continuar = false;
                    break;
                } else if (continuarStr.equals("s")) {
                    System.out.print("Introduzca la nueva query: ");
                    queryContent = console.nextLine();
                    System.out.println();
                    break;
                }
            }
        }

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
