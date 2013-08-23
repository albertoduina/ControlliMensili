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
	public final void testDecoderLimiti() {

		String[][] limiti = new InputOutput().readFile6("LIMITI.csv");
		MyLog.logMatrix(limiti, "limiti");
		MyLog.waitHere();
		String[] result = p12rmn_.decoderLimiti(limiti, "P10MAX");
		MyLog.logVector(result, "result");

	}

	@Test
	public final void testMainUniforTestGe() {

		// 16 dec 2011 sistemato, ora funziona in automatico
		boolean verbose = true;
		boolean ok = p12rmn_.selfTestGe(verbose);
		assertTrue(ok);
	}

	@Test
	public final void testMainUniforTestSiemens() {

		// 16 dec 2011 sistemato, ora funziona in automatico
		boolean verbose = true;
		boolean ok = p12rmn_.selfTestSiemens(verbose);
		assertTrue(ok);
	}

	@Test
	public final void testSelfTestSilent() {
		new p12rmn_().selfTestSilent();
	}

	@Test
	public final void testMainUniforFast() {

		// 16 dec 2011 sistemato, ora funziona in automatico

		String path1 = "./Test2/C001_testP10";
		String path2 = "./Test2/C002_testP10";
		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = false;
		double profond = 30;
		boolean fast = true;
		ResultsTable rt1 = p12rmn_.mainUnifor(path1, path2, autoArgs, profond,
				"info10", autoCalled, step, verbose, test, fast);

		double[] vetResults = UtilAyv.vectorizeResults(rt1);
		double[] vetReference = new p12rmn_().referenceSiemens();

		double simul = 0.0;

		String[] vetName = { "simul", "signal", "backNoise", "snRatio", "fwhm",
				"num1", "num2", "num3", "num4", "num5", "num6", "num7", "num8",
				"num9", "num10", "num11", "num12" };

		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference, vetName);
		assertTrue(ok);
	}

	@Test
	public final void testMainUniforFault() {

		// 16 dec 2011 sistemato, ora funziona in automatico

		String path1 = "./data/F001_testP10";
		String path2 = "./data/F002_testP10";
		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = false;
		double profond = 30;
		boolean fast = true;
		ResultsTable rt1 = p12rmn_.mainUnifor(path1, path2, autoArgs, profond,
				"info10", autoCalled, step, verbose, test, fast);

		double[] vetResults = UtilAyv.vectorizeResults(rt1);
		double fwhm = vetResults[4];
		MyLog.waitHere("fwhm= " + fwhm);

		double[] vetReference = new p12rmn_().referenceSiemens();
		String[] vetName = { "simul", "signal", "backNoise", "snRatio", "fwhm",
				"num1", "num2", "num3", "num4", "num5", "num6", "num7", "num8",
				"num9", "num10", "num11", "num12" };
		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference, vetName);
		assertTrue(ok);
	}

	@Test
	public final void testMainUniforSlow() {

		// 16 dec 2011 sistemato, ora funziona in automatico

		String path1 = "./Test2/C001_testP10";
		String path2 = "./Test2/C002_testP10";
		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = true;
		boolean verbose = true;
		boolean test = false;
		double profond = 30;
		boolean fast = false;
		ResultsTable rt1 = p12rmn_.mainUnifor(path1, path2, autoArgs, profond,
				"info10", autoCalled, step, verbose, test, fast);

		double[] vetResults = UtilAyv.vectorizeResults(rt1);
		double[] vetReference = new p12rmn_().referenceSiemens();
		MyLog.logVector(vetResults, "vetResults");
		MyLog.logVector(vetReference, "vetReference");
		String[] vetName = { "simul", "signal", "backNoise", "snRatio", "fwhm",
				"num1", "num2", "num3", "num4", "num5", "num6", "num7", "num8",
				"num9", "num10", "num11", "num12" };

		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference, vetName);
		assertTrue(ok);
	}

	@Test
	public final void testPositionSearch11() {

		// 16 dic 2011 sistemato, ora funziona in automatico

		// String path1 = "./Test2/HUSA_001testP3";

	//	String path1 = "./data/P12/0001";

		String path2 = "c:\\dati\\000P12";
		String[] list = new File(path2).list();
		if (list == null)
			return;
		for (int i1 = 0; i1 < list.length; i1++) {
			String path1= path2+"\\"+list[i1];
//			MyLog.waitHere("path1= "+path1);
			ImagePlus imp11 = UtilAyv.openImageNoDisplay(path1, true);
			
			boolean autoCalled = false;
			boolean step = true;
			boolean verbose = true;
			boolean test = false;
			boolean fast = false;

			int out2[] = p12rmn_.positionSearch11(imp11, "", autoCalled, step,
					verbose, test, fast);

			MyLog.logVector(out2, "out2");

//			int[] expected = { 126, 115, 172, 126, 39, 153 };
//			MyLog.logVector(expected, "expected");
//			boolean ok = UtilAyv.compareVectors(out2, expected, "");
//			assertTrue(ok);
		}
	}

	@Test
	public final void testPositionSearch12() {

		// 16 dic 2011 sistemato, ora funziona in automatico

		String path1 = "./Test2/HUSA_001testP3";

		String path2 = "./Test2/HUSA_002testP3";
		ImagePlus imp11 = UtilAyv.openImageNoDisplay(path1, true);
		imp11.show();

		ImagePlus imp13 = UtilAyv.openImageNoDisplay(path2, true);
		imp11.show();

		boolean autoCalled = false;
		boolean step = true;
		boolean verbose = true;
		boolean test = false;
		boolean fast = false;

		int out2[] = p12rmn_.positionSearch12(imp11, imp13, "", autoCalled,
				step, verbose, test, fast);

		MyLog.logVector(out2, "out2");

		double[] expected = { 159.0, 105.0, 118.0, 133.0, 202.0, 77.0,
				33.690067525979785 };
		// MyLog.logVector(out2, "out2");
		// boolean ok = UtilAyv.verifyResults1(expected, out2, null);
		// assertTrue(ok);
	}

	@Test
	public final void testPositionSearch13() {

		// 16 dic 2011 sistemato, ora funziona in automatico

		String path1 = "./Test2/aaa.tif";
		ImagePlus imp11 = UtilAyv.openImageNoDisplay(path1, true);
		imp11.deleteRoi();
		ImagePlus imp13 = UtilAyv.openImageNoDisplay(path1, true);
		imp13.deleteRoi();
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = false;
		boolean fast = true;

		int[] out2 = p12rmn_.positionSearch12(imp11, imp13, "", autoCalled,
				step, verbose, test, fast);
		MyLog.logVector(out2, "out2");
		int[] circleData = out2;
		int diamGhost = 20;
		int guard = 10;
		int[] out3 = p12rmn_.positionSearch13(imp11, circleData, diamGhost,
				guard, "", autoCalled, step, verbose, test, fast);
		MyLog.logVector(out3, "out3");
		MyLog.waitHere();

		path1 = "./Test2/bbb.tif";
		imp11 = UtilAyv.openImageNoDisplay(path1, true);
		imp11.deleteRoi();
		imp13 = UtilAyv.openImageNoDisplay(path1, true);
		imp13.deleteRoi();

		out2 = p12rmn_.positionSearch12(imp11, imp13, "", autoCalled, step,
				verbose, test, fast);
		MyLog.logVector(out2, "out2");
		circleData = out2;
		out3 = p12rmn_.positionSearch13(imp11, circleData, diamGhost, guard,
				"", autoCalled, step, verbose, test, fast);
		MyLog.logVector(out3, "out3");

		double[] expected = { 159.0, 105.0, 118.0, 133.0, 202.0, 77.0,
				33.690067525979785 };
		// MyLog.logVector(out2, "out2");
		// boolean ok = UtilAyv.verifyResults1(expected, out2, null);
		// assertTrue(ok);
	}

	@Test
	public final void testPositionSearch14() {

		// 16 dic 2011 sistemato, ora funziona in automatico

		String path1 = "./Test2/HUSA2_01testP3";
		ImagePlus imp11 = UtilAyv.openImageNoDisplay(path1, true);
		imp11.deleteRoi();
		boolean autoCalled = true;
		boolean step = false;
		boolean verbose = false;
		boolean test = false;
		boolean fast = true;
		boolean irraggiungibile = false;

		int[] out2 = p12rmn_.positionSearch11(imp11, "", autoCalled, step,
				verbose, test, fast);
		MyLog.logVector(out2, "out2");
		int[] circleData = out2;
		int diamGhost = 20;
		int guard = 10;
		int[] out3 = p12rmn_.positionSearch14(imp11, circleData, diamGhost,
				guard, "", autoCalled, step, verbose, test, fast,
				irraggiungibile);
		MyLog.logVector(out3, "out3");
		MyLog.waitHere();

		irraggiungibile = true;
		MyLog.waitHere("irraggiungibile= " + irraggiungibile);
		out3 = p12rmn_.positionSearch14(imp11, circleData, diamGhost, guard,
				"", autoCalled, step, verbose, test, fast, irraggiungibile);
		MyLog.logVector(out3, "out3");
		MyLog.waitHere();

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
		MyLog.waitHere("out3= " + out3);

	}

	@Test
	public final void testCriticalDistanceCalculation() {
		int x1 = 55;
		int y1 = 22;
		int r1 = 19;
		int x2 = 129;
		int y2 = 140;
		int r2 = 86;
		int out1 = p12rmn_.criticalDistanceCalculation(x1, y1, r1, x2, y2, r2);
		MyLog.waitHere("out1= " + out1);

	}

	@Test
	public final void testInterpola() {
		double ax = 117.0;
		double ay = 45.0;
		double bx = 70.0;
		double by = 78.0;
		double prof = 20.0;
		double[] out = p12rmn_.interpolaProfondCentroROI(ax, ay, bx, by, prof);
		MyLog.logVector(out, "out");

	}

	@Test
	public final void testAngoloRad() {
		double ax = 117.0;
		double ay = 45.0;
		double bx = 70.0;
		double by = 78.0;
		// double prof = 20.0;
		double out = p12rmn_.angoloRad(ax, ay, bx, by);
		IJ.log("angoloRad= " + out + " angoloDeg= "
				+ IJ.d2s(Math.toDegrees(out)));
	}

	@Test
	public final void testCreateErf() {

		// 16 dec 2011 sistemato, ora funziona in automatico senza bisogno di
		// visualizzare il profilo

		double[] profile1 = InputOutput
				.readDoubleArrayFromFile((new InputOutput()
						.findResource("/002.txt")));
		MyLog.logVector(profile1, "profile1");

		double[] smooth1 = p12rmn_.smooth3(profile1, 3);

		boolean invert = true;

		double[] profile2 = p12rmn_.createErf(smooth1, invert);
		MyLog.logVector(profile2, "profile2");

		double[] expected = InputOutput
				.readDoubleArrayFromFile((new InputOutput()
						.findResource("/003.txt")));

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
	public final void testPeakDet() {

		// 16 dec 2011 sistemato, ora funziona in automatico senza bisogno di
		// visualizzare il profilo

		double[][] profile1 = InputOutput
				.readDoubleMatrixFromFile((new InputOutput()
						.findResource("/BADProfile.txt")));
		double delta = 100.0;
		new p12rmn_();
		ArrayList<ArrayList<Double>> matOut = p12rmn_.peakDet(profile1, delta);
		double[][] out = new InputOutput().fromArrayListToDoubleTable(matOut);

		double[] vetx = new double[profile1.length];
		double[] vety = new double[profile1.length];

		for (int j = 0; j < profile1.length; j++)
			vetx[j] = profile1[j][0];
		for (int j = 0; j < profile1.length; j++)
			vety[j] = profile1[j][1];

		// Plot plot2 = MyPlot.basePlot(vetx, vety, "P R O F I L O",
		// Color.blue);
		// plot2.show();
		// new WaitForUserDialog("Do something, then click OK.").show();
		// IJ.wait(200);

		double expected = 66.796875;
		assertEquals(expected, out[0][0], 1e-12);
		expected = 14.09287783266325;
		assertEquals(expected, out[1][0], 1e-12);
		expected = 9.9609375;
		assertEquals(expected, out[2][0], 1e-12);
		expected = 128.3203125;
		assertEquals(expected, out[2][1], 1e-12);
		expected = 445.2493818993196;
		assertEquals(expected, out[3][0], 1e-12);
		expected = 199.34767076939997;
		assertEquals(expected, out[3][1], 1e-12);
	}

	@Test
	public final void testPeakDetAAAAAA() {

		// 16 dec 2011 sistemato, ora funziona in automatico senza bisogno di
		// visualizzare il profilo

		double[][] profile1 = InputOutput
				.readDoubleMatrixFromFile((new InputOutput()
						.findResource("/profi3.txt")));
		double delta = 100.0;
		new p12rmn_();
		ArrayList<ArrayList<Double>> matOut = p12rmn_.peakDet(profile1, delta);
		double[][] out = new InputOutput().fromArrayListToDoubleTable(matOut);

		double[] vetx = new double[profile1.length];
		double[] vety = new double[profile1.length];

		for (int j = 0; j < profile1.length; j++)
			vetx[j] = profile1[j][0];
		for (int j = 0; j < profile1.length; j++)
			vety[j] = profile1[j][1];

		Plot plot2 = MyPlot.basePlot(vetx, vety, "P R O F I L O", Color.blue);
		plot2.show();
		new WaitForUserDialog("Do something, then click OK.").show();
		IJ.wait(200);

		MyLog.logMatrix(out, "out");
		MyLog.waitHere();

		double expected = 66.796875;
		assertEquals(expected, out[0][0], 1e-12);
		expected = 14.09287783266325;
		assertEquals(expected, out[1][0], 1e-12);
		expected = 9.9609375;
		assertEquals(expected, out[2][0], 1e-12);
		expected = 128.3203125;
		assertEquals(expected, out[2][1], 1e-12);
		expected = 445.2493818993196;
		assertEquals(expected, out[3][0], 1e-12);
		expected = 199.34767076939997;
		assertEquals(expected, out[3][1], 1e-12);
	}

	@Test
	public final void testFromPointsToEquLineExplicit() {

		double x1 = 0;
		double y1 = 0;
		double x2 = 255;
		double y2 = 0;
		double[] out1 = null;

		x1 = 0;
		y1 = 0;
		x2 = 0;
		y2 = 255;

		out1 = p12rmn_.fromPointsToEquLineExplicit(x1, y1, x2, y2);

		MyLog.logVector(out1, "out1");
	}

	@Test
	public final void testFromPointsToEquLineImplicit() {

		double x1 = 0;
		double y1 = 0;
		double x2 = 255;
		double y2 = 0;
		double[] out1 = null;

		x1 = 120;
		y1 = 30;
		x2 = 120;
		y2 = 50;

		out1 = p12rmn_.fromPointsToEquLineImplicit(x1, y1, x2, y2);

		MyLog.logVector(out1, "out1");
	}

	@Test
	public final void testLiangBarsky() {

		double edgeLeft = 0.;
		double edgeRight = 255.;
		double edgeBottom = 0.;
		double edgeTop = 255.;
		double x0src = 20.;
		double y0src = -30.;
		double x1src = 200.;
		double y1src = 290.;

		double[] out = p12rmn_.liangBarsky(edgeLeft, edgeRight, edgeBottom,
				edgeTop, x0src, y0src, x1src, y1src);

		MyLog.logVector(out, "out");
	}

	@Test
	public final void testCrossing() {

		double x0 = 54.14;
		double y0 = 99.69;
		double x1 = 94.53;
		double y1 = 57.58;
		double width = 220;
		double height = 220;

		double[] out = p12rmn_.crossing(x0, y0, x1, y1, width, height);
		MyLog.logVector(out, "out");

		double[] vetResults1 = { 149.75812158632155, 0.0, 0.0, 156.135540975489 };
		boolean ok = UtilAyv.verifyResults1(vetResults1, out, null);
		assertTrue(ok);

		x0 = 25.78;
		y0 = 205.05868;
		x1 = 151.25;
		y1 = 132.34;

		out = p12rmn_.crossing(x0, y0, x1, y1, width, height);
		MyLog.logVector(out, "out");

		double[] vetResults2 = { 2.0627437110885412E-6, 220.0, 220.0,
				92.49454491113413 };
		ok = UtilAyv.verifyResults1(vetResults2, out, null);
		assertTrue(ok);

	}

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

	@Test
	public final void testHorizontalScanLeftBubble() {

		// String path1 = "./Test2/C001_testP10";
		String path1 = "data/67226879";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);

		boolean scan = true;
		p12rmn_.horizontalScan(imp1, scan);

	}

	@Test
	public final void testHorizontalScanHighBubble() {

		// String path1 = "./Test2/C001_testP10";
		String path1 = "data/67226831";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);

		boolean scan = true;
		p12rmn_.horizontalScan(imp1, scan);

	}

	@Test
	public final void testInsideOutside() {

		// String path1 = "./Test2/C001_testP10";
		String path1 = "data/P12/0009";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);

		boolean scan = true;
		int low = 100;
		int high = 1000;
		ImagePlus imp10 = p12rmn_.insideOutside(imp1, low, high, scan);
		UtilAyv.showImageMaximized(imp10);

		MyLog.waitHere();

	}

	@Test
	public final void testCanny() {

		// // String path1 = "./Test2/C001_testP10";
		// String path1 = "data/P12/0009";
		// ImagePlus imp1 = UtilAyv.openImageMaximized(path1);

		String path2 = "data/P12/";
		Sequenze_ sq1 = new Sequenze_();
		List<File> list1 = sq1.getFileListing((new File(path2)));
		String[] list2 = new String[list1.size()];
		int j1 = 0;
		for (File file : list1) {
			list2[j1++] = file.getPath();
			ImagePlus imp1 = UtilAyv.openImageMaximized(file.getPath());
			float low = 3.01f;
			float high = 10.0f;
			float radius = 2.0f;
			boolean normalized = false;

			ImagePlus imp10 = p12rmn_
					.canny(imp1, low, high, radius, normalized);
			UtilAyv.showImageMaximized(imp10);
			MyLog.waitHere();
			imp1.close();
			imp10.close();
		}

	}

	@Test
	public final void testCanny2() {

		// String path1 = "./Test2/C001_testP10";
		String path1 = "data/P12/0010";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);

		float low = 3.01f;
		float high = 10.0f;
		float radius = 2.0f;
		boolean normalized = false;

		ImagePlus imp10 = p12rmn_.canny(imp1, low, high, radius, normalized);
		UtilAyv.showImageMaximized(imp10);
		MyLog.waitHere();
		imp10.close();
		ImagePlus imp2 = UtilAyv.openImageNoDisplay(path1, false);
		ImageProcessor ip2 = imp2.getProcessor();
		ip2.invert();
		imp2.updateImage();

		UtilAyv.showImageMaximized(imp2);
		MyLog.waitHere();

		ImagePlus imp11 = p12rmn_.canny(imp2, low, high, radius, normalized);
		UtilAyv.showImageMaximized(imp11);
		MyLog.waitHere();

	}

}
