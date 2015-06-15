package contMensili;

import static org.junit.Assert.*;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.WaitForUserDialog;
import ij.measure.ResultsTable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import utils.InputOutput;
import utils.MyConst;
import utils.MyLog;
import utils.UtilAyv;

public class p11rmn_Test {
	@Before
	public void setUp() throws Exception {
		new ImageJ(ImageJ.NORMAL);
	}

	@After
	public void tearDown() throws Exception {
		// new WaitForUserDialog("Do something, then click OK.").show();

	}

	@Test
	public final void testMainUnifor() {

		String path1 = "./Test2/S12S_01testP11";
		String path2 = "./Test2/S12S_02testP11";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = false;
		int verticalDir = 3;
		double[] vetReference = p11rmn_.referenceSiemens();
		double profond = 30.0;
		boolean fast = true;
		boolean silent = false;
		int timeout=100;
		ResultsTable rt1 = p11rmn_.mainUnifor(path1, path2, verticalDir,
				profond, "", autoCalled, step, verbose, test, fast, silent, timeout);
		double[] vetResults = UtilAyv.vectorizeResults(rt1);
		// MyLog.waitHere();
		// MyLog.logVector(vetResults, "vetResults");
		// MyLog.logVector(vetReference, "vetReference");
		// MyLog.waitHere();
		assertTrue(UtilAyv.compareVectors(vetResults, vetReference, 1e-12, ""));
	}

	@Test
	public final void testMainUniforTestSiemens() {

		// 16 dec 2011 sistemato, ora funziona in automatico
		boolean verbose = true;
		boolean ok = p11rmn_.selfTestSiemens(verbose);
		assertTrue(ok);
	}

	@Test
	public final void testMainUniforTestGe() {

		// 16 dec 2011 sistemato, ora funziona in automatico
		boolean verbose = true;
		boolean ok = p11rmn_.selfTestGe(verbose);
		assertTrue(ok);
	}

	@Test
	public final void testMainUniforTestSilent() {
		new p11rmn_().selfTestSilent();
	}

	@Test
	public final void testOverlayGrid() {

		ImagePlus imp1 = UtilAyv.openImageMaximized(".\\Test2\\S1SA_01testP11");
		boolean verbose = true;
		p11rmn_.overlayGrid(imp1, MyConst.P11_GRID_NUMBER, verbose);
		IJ.wait(300);

	}

//	@Test
//	public final void testMaxPeakSearch() {
//
//		String path = InputOutput.findResource("002.txt");
//		double[] profile1 = InputOutput.readDoubleArrayFromFile(path);
//		double[] out = p11rmn_.maxPeakSearch(profile1);
//		double[] expected = { 96.0, 531.1009869960047 };
//		assertTrue(UtilAyv.compareVectors(expected, out, 1e-12, ""));
//	}

	@Test
	public final void testPositionSearch() {

		// 16 dec 2011 sistemato, ora funziona in automatico

		boolean autoCalled = false;
		int verticalDir = 3;
		double profond = 30.0;
		boolean step = true;
		boolean verbose = true;
		boolean test = true;
		boolean fast = false;
		boolean silent = false;
		int timeout=0;
		String path1 = "./Test2/S12S_01testP11";
//		ImagePlus imp1 = UtilAyv.openImageNoDisplay(path1, true);
		ImagePlus imp1 = UtilAyv.openImageNoDisplay(path1, true);
		double[] out = p11rmn_.positionSearch(imp1, autoCalled, verticalDir,
				profond, "TestPositionSearch", step, verbose, test, fast, silent, timeout);
		double[] expected = { 164.0, 172.0, 164.05714285714285, 0.0,
				164.05714285714285, 256.0, 186.0, 169.0 };
		assertTrue(UtilAyv.compareVectors(expected, out, 1e-12, ""));
	}

	@Test
	public final void testDevStandardNema() {

		String path1 = "./Test2/1_SLFS";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		String path2 = "./Test2/2_SLFS";
		ImagePlus imp2 = UtilAyv.openImageNoDisplay(path2, true);
		ImagePlus imp3 = UtilAyv.genImaDifference(imp1, imp2);

		// UtilAyv.showImageMaximized(imp3);
		int sqX = 124;
		int sqY = 203;
		int sqR = 11;
		double limit = 11.2;

		double[] devStand = p11rmn_.devStandardNema(imp1, imp3, sqX, sqY, sqR,
				limit);
		// IJ.log("p11rmn.devStand= " + devStand[1]);
		double expected = 0.9569954129386802;
		assertEquals(expected, devStand[1], 1e-12);

	}

	@Test
	public final void testSelfTestSilent() {
		new p11rmn_().selfTestSilent();
		
	}

}
