package es.udc.fi.ri.mri_searcher;

import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

public class Compare {

    private Compare() {
    }


    public static void main(String[] args) throws Exception {
        String usage = "Usage: java es.udc.fi.ri.mri_searcher.Compare -results FILE_RESULTS_1 FILE_RESULTS_2" +
                " -test TEST_TYPE ALPHA\n\n";

        if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
            System.out.println(usage);
            System.exit(0);
        }

        /*
        String searchModel = null;
        String searchStat;
        Similarity similarity = null;
        String index = "index";
        int cut = -1;
        String metrica = null;
        int top = -1;
        String queries = null;
        String field = "Contents";
        */

        String results1 = null;
        String results2 = null;
        String testType = null;
        float paramAlpha = -1;

        for (int i = 0; i < args.length; i++) {
            if ("-results".equals(args[i])) {
                results1 = args[i + 1];
                results2 = args[i + 2];
                i++;
            } else if ("-test".equals(args[i])) {
                testType = args[i + 1];
                paramAlpha = Float.parseFloat(args[i + 2]);
                i++;
            }
        }

        if (results1 == null || results2 == null || testType == null || paramAlpha == -1) {
            System.err.println(usage);
            System.exit(1);
        } else if ( !testType.equals("t") && !testType.equals("wilcoxon")) {
            System.err.println("The -test parameter must have one of the following values: {t|wilcoxon alpha}");
            System.exit(1);
        }


        // Leemos los archivos
        BufferedReader r1 = Files.newBufferedReader(Paths.get(results1), StandardCharsets.UTF_8);
        BufferedReader r2 = Files.newBufferedReader(Paths.get(results2), StandardCharsets.UTF_8);

        ArrayList<Double> list1 = new ArrayList<>();
        ArrayList<Double> list2 = new ArrayList<>();

        String line;
        String[] aux = null;

        line = r1.readLine();
        while ( (line != null) && (!line.equals("")) ) {
            aux = line.split(", ");
            list1.add(Double.parseDouble(aux[1]));
            line = r1.readLine();
        }

        line = r2.readLine();
        while ( (line != null) && (!line.equals("")) ) {
            aux = line.split(", ");
            list2.add(Double.parseDouble(aux[1]));
            line = r2.readLine();
        }


        double[] arr1 = new double[list1.size()];
        double[] arr2 = new double[list2.size()];

        for (int i=0; i < list1.size(); i++) {
            arr1[i] = list1.get(i);
        }

        for (int i=0; i < list2.size(); i++) {
            arr2[i] = list2.get(i);
        }

        // Imprimimos los arrays por pantalla
        System.out.println("Archivo con results1: " + results1);
        System.out.println("Archivo con results2: " + results2);
        System.out.println();

        System.out.println("Array con results1: " + Arrays.toString(arr1));
        System.out.println("Array con results2: " + Arrays.toString(arr2));
        System.out.println();

        // Realizamos el test de significancia
        double pvalor = -1;
        String testName = null;

        if (testType.equals("t")) {
            testName = "t‐Test";
            TTest t = new TTest();
            pvalor = t.pairedTTest(arr1, arr2);

        } else if (testType.equals("wilcoxon")) {
            testName = "Wilcoxon Test";
            WilcoxonSignedRankTest t = new WilcoxonSignedRankTest();
            //pvalor = t.wilcoxonSignedRank(arr1, arr2);
            pvalor = t.wilcoxonSignedRankTest(arr1, arr2, false);
        }

        System.out.println("Realizado " + testName + " (con alpha = " + paramAlpha + "). Resultados del test: ");
        System.out.println("p-valor = " + pvalor);
        if (pvalor <= paramAlpha) {
            System.out.println("Se concluye que EXISTE una diferencia estadísticamente significativa");
        } else {
            System.out.println("Se concluye que NO EXISTE una diferencia estadísticamente significativa");
        }
    }
}
