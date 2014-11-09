package contMensili;

import static org.junit.Assert.assertTrue;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.ResultsTable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import utils.MyConst;
import utils.MyLog;
import utils.UtilAyv;

public class p17rmn_Test {

	@Before
	public void setUp() throws Exception {
		new ImageJ(ImageJ.NORMAL);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public final void testAutomaticRoiPreparationTest3() {

		String path1 = ".\\Test2\\HWSA_testP7";
		ImagePlus imp1 = UtilAyv.openImageNoDisplay(path1, false);
		boolean demo = true;
		int diam = 10;
		int timeout = 2000;
		int[][] pippo = p20rmn_.automaticRoiPreparation3(imp1, diam, timeout,
				demo);

		MyLog.waitHere();

	}

	@Test
	public final void testMainWarpFast() {

		// 04 sep 2013 funziona in automatico

		String path1 = "./Test2//HUSA_001testP3";
		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean demo = false;
		boolean test = false;
		boolean fast = true;
		boolean silent = false;
		boolean verbose = false;
		int timeout = 100;

		ResultsTable rt1 = p17rmn_.mainWarp(path1, autoCalled, step, verbose,
				test);
		double[] vetResults = UtilAyv.vectorizeResults(rt1);
		double[] vetReference = p12rmn_.referenceSiemens();
		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				MyConst.P3_vetName);
		assertTrue(ok);
	}

}