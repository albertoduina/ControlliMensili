package contMensili;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Plot;
import ij.gui.WaitForUserDialog;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import utils.InputOutput;
import utils.MyConst;
import utils.MyLog;
import utils.MyPlot;
import utils.UtilAyv;

public class p12rmn_Test {
	@Before
	public void setUp() throws Exception {
		new ImageJ(ImageJ.NORMAL);
	}

	@After
	public void tearDown() throws Exception {
		// new WaitForUserDialog("Do something, then click OK.").show();

	}

	@Test
	public final void testMainUniforTestSiemens() {

		// 16 dec 2011 sistemato, ora funziona in automatico
		boolean verbose = false;

		boolean ok = new p12rmn_().selfTestSiemens(verbose);

		assertTrue(ok);
	}

	@Test
	public final void testSelfTestSilent() {
		new p12rmn_().selfTestSilent();
	}

	@Test
	public final void testMainUniforFast() {

		// 04 sep 2013 funziona in automatico

		String path1 = "./Test2//HUSA_001testP3";
		String path2 = "./Test2//HUSA_002testP3";
		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean demo = false;
		boolean test = false;
		boolean fast = true;
		boolean silent = false;
		int timeout = 100;

		ResultsTable rt1 = p12rmn_.mainUnifor(path1, path2, autoArgs, "info10",
				autoCalled, step, demo, test, fast, silent, timeout);
		double[] vetResults = UtilAyv.vectorizeResults(rt1);
		double[] vetReference = p12rmn_.referenceSiemens();
		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				MyConst.P3_vetName);
		assertTrue(ok);
	}

	@Test
	public final void testMainUniforSlow() {

		// 04 sep 2013 funziona in automatico

		String path1 = "./Test2//HUSA_001testP3";
		String path2 = "./Test2//HUSA_002testP3";
		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean demo = true;
		boolean test = false;
		boolean fast = true;
		boolean silent = false;
		int timeout = 100;

		ResultsTable rt1 = p12rmn_.mainUnifor(path1, path2, autoArgs, "info10",
				autoCalled, step, demo, test, fast, silent, timeout);
		double[] vetResults = UtilAyv.vectorizeResults(rt1);
		double[] vetReference = new p12rmn_().referenceSiemens();
		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				MyConst.P3_vetName);
		assertTrue(ok);
	}

	@Test
	public final void testMainUniforFastManual() {

		//
		// POICHE' IN QUESTA MODALITA' IL PROGRAMMA
		// SI ARRESTA, CHIEDENDO DI CORREGGERE MANUALMENTE
		// UN POSIZIONAMENTO, NON HA SENSO TESTARLO IN AUTOMATICO
		// (NON SI OTTIENE COMUNQUE UN TEST VALIDO)
		//

		String path1 = "./Test2//HUSA_001testP3";
		String path2 = "./Test2//HUSA_002testP3";
		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean demo = false;
		boolean test = true;
		boolean fast = true;
		boolean silent = false;
		int timeout = 100;
		ResultsTable rt1 = p12rmn_.mainUnifor(path1, path2, autoArgs, "info10",
				autoCalled, step, demo, test, fast, silent, timeout);
	}

	@Test
	public final void testPositionSearch11single() {

		// 16 dic 2011 sistemato, ora funziona in automatico

		String path1 = "./Test2/HUSA_001testP3";

		// String path1 = "c:\\dati\\000P12\\68_1";

		ImagePlus imp11 = UtilAyv.openImageNoDisplay(path1, true);

		boolean autoCalled = false;
		boolean step = true;
		boolean demo = false;
		boolean test = false;
		boolean fast = false;
		double maxFitError = 5;
		double maxBubbleGapLimit = 2;
		int timeout = 2000;

		double out2[] = p12rmn_.positionSearch11(imp11, maxFitError,
				maxBubbleGapLimit, "", autoCalled, step, demo, test, fast,
				timeout);

		// MyLog.logVector(out2, "out2");
		// MyLog.waitHere();

		// 127.0, 116.0, 174.0, 127.0, 116.0, 155.0
		// 126.0, 115.0, 173.0, 126.0, 115.0, 154.0
		double[] expected = { 127.0, 116.0, 174.0, 127.0, 116.0, 155.0 };
		// MyLog.logVector(expected, "expected");
		boolean ok = UtilAyv.compareVectors(out2, expected, 0.001, "");
		assertTrue(ok);

	}

	// @Test
	// public final void testPositionSearch11() {
	//
	// // 16 dic 2011 sistemato, ora funziona in automatico
	//
	// // String path1 = "./Test2/HUSA_001testP3";
	//
	// // String path1 = "./data/P12/0001";
	//
	// String path2 = "c:\\dati\\000P12";
	// String[] list = new File(path2).list();
	// if (list == null)
	// return;
	// for (int i1 = 0; i1 < list.length; i1++) {
	// String path1 = path2 + "\\" + list[i1];
	// ImagePlus imp11 = UtilAyv.openImageNoDisplay(path1, true);
	//
	// boolean autoCalled = false;
	// boolean step = false;
	// boolean demo = false;
	// boolean test = true;
	// boolean fast = false;
	// double maxFitError = 5;
	//
	// int out2[] = p12rmn_.positionSearch11(imp11, maxFitError, "",
	// autoCalled, step, demo, test, fast);
	//
	// MyLog.logVector(out2, "out2");
	// MyLog.waitHere();
	//
	// // int[] expected = { 126, 115, 172, 126, 39, 153 };
	// // MyLog.logVector(expected, "expected");
	// // boolean ok = UtilAyv.compareVectors(out2, expected, "");
	// // assertTrue(ok);
	// }
	// }

	@Test
	public final void testPositionSearch13() {

		boolean autoCalled = false;
		boolean step = false;
		boolean demo = false;
		boolean test = false;
		boolean fast = true;
		double maxFitError = 10;
		int timeout = 2000;

		double maxBubbleGapLimit = 2;
		String info1 = "";

		String path2 = "./Test2//HUSA_001testP3";
		String[] list = new String[1];
		list[0] = path2;
		for (int i1 = 0; i1 < list.length; i1++) {

			String path1 = list[i1];
			// ImagePlus imp13 = UtilAyv.openImageMaximized(path1);
			ImagePlus imp13 = UtilAyv.openImageNoDisplay(path1, demo);
			imp13.deleteRoi();

			double[] circleData = p12rmn_.positionSearch11(imp13, maxFitError,
					maxBubbleGapLimit, info1, autoCalled, step, demo, test,
					fast, 0);
			int diamGhost = 20;
			int guard = 10;
			demo = true;
			double[][] out3 = p12rmn_.positionSearch13(imp13, circleData,
					diamGhost, guard, "", autoCalled, step, demo, test, fast,
					timeout);
			double[][] expected = { { 156.0, 12.0, 204.0, 156.0 },
					{ 246.0, 189.0, 189.0, 10.0 } };
			boolean ok = UtilAyv.compareMatrix(out3, expected, "");
			assertTrue(ok);

		}

	}

	@Test
	public final void testPositionSearch14() {

		// 16 dic 2011 sistemato, ora funziona in automatico

		String path1 = "./Test2/HUSA2_01testP3";
		ImagePlus imp11 = UtilAyv.openImageNoDisplay(path1, true);
		imp11.deleteRoi();

		boolean autoCalled = false;
		boolean step = false;
		boolean demo = false;
		boolean test = false;
		boolean fast = true;
		int timeout = 100;

		double maxFitError = 10;
		double maxBubbleGapLimit = 2;
		boolean irraggiungibile = false;

		double[] circleData = p12rmn_.positionSearch11(imp11, maxFitError,
				maxBubbleGapLimit, "", autoCalled, step, demo, test, fast, 100);

		int diamGhost = 20;
		int guard = 10;
		int[] out3 = p12rmn_.positionSearch14(imp11, circleData, diamGhost,
				guard, "", autoCalled, step, demo, test, fast, irraggiungibile,
				timeout);

		// MyLog.logVector(out3, "out3");
		// MyLog.waitHere();

		int[] expected = { 228, 228, 20 };
		boolean ok = UtilAyv.compareVectors(out3, expected, "");
		assertTrue(ok);

	}

	@Test
	public final void testVerifyCircularRoiPixels() {

		// 16 dic 2011 sistemato, ora funziona in automatico

		String path1 = "./Test2/HUSA2_01testP3";
		ImagePlus imp11 = UtilAyv.openImageNoDisplay(path1, true);
		imp11.deleteRoi();
		int xRoi = 240;
		int yRoi = 240;
		int diamRoi = 20;
		boolean test = true;
		boolean demo = false;
		boolean out3 = p12rmn_.verifyCircularRoiPixels(imp11, xRoi, yRoi,
				diamRoi, test, demo);
		// MyLog.waitHere("out3= " + out3);
		assertTrue(out3);

	}

	// @Test
	// public final void testCriticalDistanceCalculation() {
	// int x1 = 55;
	// int y1 = 22;
	// int r1 = 19;
	// int x2 = 129;
	// int y2 = 140;
	// int r2 = 86;
	// int out1 = p12rmn_.criticalDistanceCalculation(x1, y1, r1, x2, y2, r2);
	// MyLog.waitHere("out1= " + out1);
	//
	// }

	// @Test
	// public final void testInterpola() {
	// double ax = 117.0;
	// double ay = 45.0;
	// double bx = 70.0;
	// double by = 78.0;
	// double prof = 20.0;
	// double[] out = p12rmn_.interpolaProfondCentroROI(ax, ay, bx, by, prof);
	// MyLog.logVector(out, "out");
	//
	// }

	// @Test
	// public final void testAngoloRad() {
	// double ax = 117.0;
	// double ay = 45.0;
	// double bx = 70.0;
	// double by = 78.0;
	// // double prof = 20.0;
	// double out = p12rmn_.angoloRad(ax, ay, bx, by);
	// IJ.log("angoloRad= " + out + " angoloDeg= "
	// + IJ.d2s(Math.toDegrees(out)));
	// }

	@Test
	public final void testCreateErf() {

		// 16 dec 2011 sistemato, ora funziona in automatico senza bisogno di
		// visualizzare il profilo

		new InputOutput();
		String fileName = InputOutput.findResource("002.txt");
		double[] profile1 = InputOutput.readDoubleArrayFromFile(fileName);
		if (profile1 == null)
			MyLog.waitHere("profile1 == null, file non trovato");
		// MyLog.logVector(profile1, "profile1");

		double[] smooth1 = p12rmn_.smooth3(profile1, 3);

		boolean invert = true;

		double[] profile2 = p12rmn_.createErf(smooth1, invert);
		// MyLog.logVector(profile2, "profile2");

		new InputOutput();
		String fileName1 = InputOutput.findResource("003.txt");
		double[] expected = InputOutput.readDoubleArrayFromFile(fileName1);

		boolean ok = UtilAyv.verifyResults1(expected, profile2, null);
		assertTrue(ok);

		// Plot plot2 = MyPlot.basePlot(profile2, "Plot profilo ERF",
		// Color.blue);
		// plot2.show();
		// MyLog.waitHere("display plot, dare OK");
		// IJ.wait(200);

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
	// new p12rmn_().createPlot(profile1, sTitolo, vetUpDwPoints, fwhm2);
	// // MyLog.waitHere();
	//
	// IJ.wait(1000);
	// }

	@Test
	public final void testCountPixTest() {

		String path1 = "./Test2/C001_testP10";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);

		int xPos = 117;
		int yPos = 117;
		int sqNEA = 20;
		double checkPixels = 58.064;
		boolean test = true;
		// rispetto a questo test ricordarsi che la roi assegnata è
		// assolutamente arbitraria, si deve solo vedere un quadrato chiaro, più
		// o meno al centro dell'immagine

		int pixx = p12rmn_.countPixTest(imp1, xPos, yPos, sqNEA, checkPixels,
				test);

		// IJ.log("pixx= " + pixx);
		IJ.wait(200);
		assertEquals(pixx, 441);
	}

	// @Test
	// public final void testInsideOutside() {
	//
	// // String path1 = "./Test2/C001_testP10";
	// String path1 = "data/P12/0009";
	// ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
	//
	// boolean scan = true;
	// int low = 100;
	// int high = 1000;
	// ImagePlus imp10 = p12rmn_.insideOutside(imp1, low, high, scan);
	// UtilAyv.showImageMaximized(imp10);
	//
	// MyLog.waitHere();
	//
	// }

	// @Test
	// public final void testCanny() {
	//
	// // // String path1 = "./Test2/C001_testP10";
	// // String path1 = "data/P12/0009";
	// // ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
	//
	// String path2 = "data/P12/";
	// Sequenze_ sq1 = new Sequenze_();
	// List<File> list1 = sq1.getFileListing((new File(path2)));
	// String[] list2 = new String[list1.size()];
	// int j1 = 0;
	// for (File file : list1) {
	// list2[j1++] = file.getPath();
	// ImagePlus imp1 = UtilAyv.openImageMaximized(file.getPath());
	// float low = 3.01f;
	// float high = 10.0f;
	// float radius = 2.0f;
	// boolean normalized = false;
	//
	// ImagePlus imp10 = p12rmn_
	// .canny(imp1, low, high, radius, normalized);
	// UtilAyv.showImageMaximized(imp10);
	// MyLog.waitHere();
	// imp1.close();
	// imp10.close();
	// }
	//
	// }

	// @Test
	// public final void testCanny2() {
	//
	// // String path1 = "./Test2/C001_testP10";
	// String path1 = "data/P12/0010";
	// ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
	//
	// float low = 3.01f;
	// float high = 10.0f;
	// float radius = 2.0f;
	// boolean normalized = false;
	//
	// ImagePlus imp10 = p12rmn_.canny(imp1, low, high, radius, normalized);
	// UtilAyv.showImageMaximized(imp10);
	// MyLog.waitHere();
	// imp10.close();
	// ImagePlus imp2 = UtilAyv.openImageNoDisplay(path1, false);
	// ImageProcessor ip2 = imp2.getProcessor();
	// ip2.invert();
	// imp2.updateImage();
	//
	// UtilAyv.showImageMaximized(imp2);
	// MyLog.waitHere();
	//
	// ImagePlus imp11 = p12rmn_.canny(imp2, low, high, radius, normalized);
	// UtilAyv.showImageMaximized(imp11);
	// MyLog.waitHere();
	//
	// }

}
