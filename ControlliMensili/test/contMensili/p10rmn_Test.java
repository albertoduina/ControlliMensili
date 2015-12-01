package contMensili;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.util.ArrayList;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.WaitForUserDialog;
import ij.measure.ResultsTable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import utils.InputOutput;
import utils.MyConst;
import utils.MyLog;
import utils.MyPlot;
import utils.UtilAyv;

public class p10rmn_Test {
	@Before
	public void setUp() throws Exception {
		new ImageJ(ImageJ.NORMAL);
	}

	@After
	public void tearDown() throws Exception {
		// new WaitForUserDialog("Do something, then click OK.").show();

	}

	@Test
	public final void testDecoderLimiti() {

		boolean absolute = false;

		// ATTENZIONE, legge il file limiti.csv dal file jar. quindi prima di
		// testare modufiche bisogna compilare almeno una volta
		String[][] limiti = new InputOutput().readFile6LIKE("LIMITI.csv", absolute);
		String[] result = p10rmn_.decoderLimiti(limiti, "DUMMY");
		String[] expected = { "DUMMY", "101", "202", "303", "404", "505", "606", "707", "808", "909", "1010", "1111" };

		assertTrue(UtilAyv.compareVectors(expected, result, ""));
	}

	@Test
	public final void testMainUniforTestGe() {

		// 16 dec 2011 sistemato, ora funziona in automatico
		boolean verbose = true;
		boolean ok = p10rmn_.selfTestGe(verbose);
		assertTrue(ok);
	}

	@Test
	public final void testMainUniforTestSiemens() {

		// 16 dec 2011 sistemato, ora funziona in automatico
		boolean verbose = true;
		boolean ok = p10rmn_.selfTestSiemens(verbose);
		assertTrue(ok);
	}

	@Test
	public final void testSelfTestSilent() {
		new p10rmn_().selfTestSilent();
	}

	@Test
	public final void testMainUniforFast() {

		// 16 dec 2011 sistemato, ora funziona in automatico

		String path1 = "./Test2/C001_testP10";
		String path2 = "./Test2/C002_testP10";
		String autoArgs = "0";
		// Valori assegnati a mode
		// mode = 1 (ex fast) modo automatico completo

		int mode = 1;
		double profond = 30;
		String reqCoil = "";
		ResultsTable rt1 = p10rmn_.mainUnifor(path1, path2, autoArgs, profond, reqCoil, "info10", mode, 100);

		// ResultsTable rt1 = p10rmn_.mainUnifor(path1, path2, autoArgs,
		// profond,
		// "info10", autoCalled, step, verbose, test, fast, silent, 100);

		double[] vetResults = UtilAyv.vectorizeResults(rt1);
		double[] vetExpected = p10rmn_.referenceSiemens();

		// MyLog.logVector(vetResults, "vetResults");
		// MyLog.logVector(vetExpected, "vetExpected");
		// MyLog.waitHere();

		assertTrue(UtilAyv.compareVectors(vetResults, vetExpected, 1e-12, ""));
	}

	@Test
	public final void testMainUniforFault() {

		// 16 dec 2011 sistemato, ora funziona in automatico

		// String path1 = "./data/F001_testP10";
		// String path2 = "./data/F002_testP10";
		String autoArgs = "0";
		String path1 = "./data/F001_testP10";
		String path2 = "./data/F002_testP10";

		int mode = 0;
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = true;
		double profond = 30;
		boolean fast = true;
		boolean silent = false;
		int timeout = 100;
		String reqCoil = "";

		// p10rmn_.mainUnifor(path1, path2, autoArgs, profond, "info10",
		// autoCalled, step, verbose, test, fast, silent, 100);
		p10rmn_.mainUnifor(path1, path2, autoArgs, profond, reqCoil, "info10", mode, 100);
		// in questo caso l'unica cosa che viene testata � l'intervento manuale.
		// Poich� il posizionamento dipende dall'occhio dell'operatore
		// non posso fare una verifica dei risultati ottenuti.
	}

	@Test
	public final void testPositionSearch() {

		// 16 dic 2011 sistemato, ora funziona in automatico

		String path1 = "./Test2/C001_testP10";

		ImagePlus imp11 = UtilAyv.openImageMaximized(path1);

		int mode = 10;

		double profond = 30;
		double out2[] = p10rmn_.positionSearch(imp11, profond, "", mode, 1000);

		double[] expected = { 158.5, 105.5, 118.0, 135.0, 196.0, 78.0, 36.52885536698517, 199.54650334804768,
				75.40832447642669, 202.0, };

		// MyLog.logVector(out2, "out2");
		// MyLog.logVector(expected, "expected");
		// MyLog.waitHere();

		assertTrue(UtilAyv.compareVectors(expected, out2, 1e-12, ""));
	}

	@Test
	public final void testInterpola() {
		double ax = 117.0;
		double ay = 45.0;
		double bx = 70.0;
		double by = 78.0;
		double prof = 20.0;
		double[] out = p10rmn_.interpolaProfondCentroROI(ax, ay, bx, by, prof);
		double[] expected = { 100.63173950947295, 56.492608429518995 };
		assertTrue(UtilAyv.compareVectors(expected, out, 1e-12, ""));
	}

	@Test
	public final void testAngoloRad() {
		double ax = 117.0;
		double ay = 45.0;
		double bx = 70.0;
		double by = 78.0;
		// double prof = 20.0;
		double out = p10rmn_.angoloRad(ax, ay, bx, by);
		// IJ.log("angoloRad= " + out + " angoloDeg= "
		// + IJ.d2s(Math.toDegrees(out)));
		assertTrue(out == 0.6121524969450833);

	}

	@Test
	public final void testCreateErf() {

		// 16 dec 2011 sistemato, ora funziona in automatico senza bisogno di
		// visualizzare il profilo

		String path = InputOutput.findResource("002.txt");

		double[] profile1 = InputOutput.readDoubleArrayFromFile(path);
		double[] smooth1 = p10rmn_.smooth3(profile1, 3);
		boolean invert = true;
		double[] profile2 = p10rmn_.createErf(smooth1, invert);
		String path2 = InputOutput.findResource("003.txt");
		double[] expected = InputOutput.readDoubleArrayFromFile(path2);
		assertTrue(UtilAyv.compareVectors(expected, profile2, 1e-12, ""));
	}

	// @Test
	// public final void testCreatePlot() {
	// double[] profile1 = InputOutput
	// .readDoubleArrayFromFile("./data/vet20.txt");
	// String sTitolo = "createPlotWork";
	//
	// int[] vetUpDwPoints = { 63, 62, 131, 132 };
	// double fwhm2 = 71.8679;
	//
	// new p10rmn_().createPlot(profile1, sTitolo, vetUpDwPoints, fwhm2);
	// // MyLog.waitHere();
	//
	// IJ.wait(1000);
	// }

	@Test
	public final void testCountPixOverLimit() {

		String path1 = "./Test2/C001_testP10";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		Overlay over1 = new Overlay();
		imp1.setOverlay(over1);

		int xPos = 117;
		int yPos = 117;
		int sqNEA = 20;
		// double checkPixels = 58.064;
		double checkPixels = 300;
		boolean test = true;
		// rispetto a questo test ricordarsi che la roi assegnata �
		// assolutamente arbitraria, si deve solo vedere un quadrato chiaro, pi�
		// o meno al centro dell'immagine

		int pixx = p10rmn_.countPixOverLimit(imp1, xPos, yPos, sqNEA, checkPixels, test, over1);

		// IJ.wait(100);
		assertEquals(pixx, 169);
	}

}
