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
	public final void testAutomaticRoiPreparationTest1_SIEMENS() {

		String path1 = ".\\Test2\\HWSA_testP7";

		ImagePlus imp1 = UtilAyv.openImageNoDisplay(path1, false);
		boolean demo = true;
		boolean silent = false;
		int diam = 10;
		int timeout = 2000;
		int[][] pippo = p17rmn_.automaticRoiPreparation3(imp1, diam, silent,
				timeout, demo);

		MyLog.waitHere();

	}

	@Test
	public final void testAutomaticRoiPreparationTest_HITACHI() {

		String path1 = ".\\Test2\\HWSA3";

		ImagePlus imp1 = UtilAyv.openImageNoDisplay(path1, false);
		boolean demo = true;
		boolean silent = false;
		int diam = 10;
		int timeout = 2000;
		int[][] pippo = p17rmn_.automaticRoiPreparation3(imp1, diam, silent,
				timeout, demo);

		MyLog.waitHere();

	}

	@Test
	public final void testNuovaStrategia() {

		String path1 = ".\\Test2\\HWSA_testP7";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		ImagePlus imp2 = p17rmn_.nuovaStrategia(imp1);
		MyLog.waitHere();
	}

	@Test
	public final void testStrategia0() {

//		String path1 = ".\\Test2\\HWSA_testP7";
		String path1 = "C:\\Dati\\_____P17\\29_71193075_HE1-4_HWSA_";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		ImagePlus imp2 = p17rmn_.strategia0(imp1);
		UtilAyv.showImageMaximized(imp2);
		MyLog.waitHere();
	}

	@Test
	public final void testStrategia1() {

		String path1 = ".\\Test2\\HWSA_testP7";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		ImagePlus imp2 = p17rmn_.strategia1(imp1);
		UtilAyv.showImageMaximized(imp2);
		MyLog.waitHere();
	}

	@Test
	public final void testStrategia2() {

//		String path1 = ".\\Test2\\HWSA2";
		String path1 = "C:\\Dati\\_____P17\\FALLATA\\21_DESENZANO2_MISS_HWSA3";
		
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		ImagePlus imp2 = p17rmn_.strategia2(imp1);
		UtilAyv.showImageMaximized(imp2);
		MyLog.waitHere();

	}

	@Test
	public final void testStrategiaGENERALE() {

		// RATS
		
//		String path1 = ".\\Test2\\HWSA2";
		String path1 = "C:\\Dati\\_____P17\\FALLATA\\21_DESENZANO2_MISS_HWSA3";
		
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		ImagePlus imp2 = p17rmn_.strategiaGENERALE(imp1);
		UtilAyv.showImageMaximized(imp2);
		MyLog.waitHere();

	}

	
	
	@Test
	public final void testStrategia5() {

//		String path1 = "C:\\Dati\\_____P17\\DESENZANO\\NUOVA\\Series_22_HWSA3-2MISS\\22_HWSA3";
		String path1 = "C:\\Dati\\_____P17\\FALLATA\\21_DESENZANO2_MISS_HWSA3";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		ImagePlus imp2 = p17rmn_.strategia5(imp1);
		UtilAyv.showImageMaximized(imp2);
		MyLog.waitHere();

	}

	
	@Test
	public final void testStrategiaSIEMENS() {

		// String path1 = ".\\Test2\\HWSA_testP7";

		// String path1 = "C:\\Dati\\_____P17\\AERA\\Series_22_HWSA\\22_74845920_HE1-4_HWSA_";
		String path1 = "C:\\Dati\\_____P17\\29_71193075_HE1-4_HWSA_";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		ImagePlus imp2 = p17rmn_.strategiaSIEMENS(imp1);
		UtilAyv.showImageMaximized(imp2);
		MyLog.waitHere();

	}

	@Test
	public final void testStrategiaGEMS() {

		String path1 = ".\\Test2\\HWSA2";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		ImagePlus imp2 = p17rmn_.strategia0(imp1);
		ImagePlus imp3 = p17rmn_.strategia1(imp1);
		ImagePlus imp4 = p17rmn_.combina(imp2, imp3);

		UtilAyv.showImageMaximized(imp4);
		MyLog.waitHere();

	}

	@Test
	public final void testStrategiaHITACHI() {

//		String path1 = ".\\Test2\\HWSA3";
		String path1 = "C:\\Dati\\_____P17\\FALLATA\\21_DESENZANO2_MISS_HWSA3";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		ImagePlus imp2 = p17rmn_.strategiaHITACHI(imp1);
		UtilAyv.showImageMaximized(imp2);
		MyLog.waitHere();

	}

	@Test
	public final void testStrategiaHITACHI2() {

//		String path1 = ".\\Test2\\HWSA3";
		String path1 = "C:\\Dati\\_____P17\\FALLATA\\21_DESENZANO2_MISS_HWSA3";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		ImagePlus imp2 = p17rmn_.strategiaHITACHI2(imp1);
		UtilAyv.showImageMaximized(imp2);
		MyLog.waitHere();

	}

	@Test
	public final void testAutomaticRoiPreparation_GE() {

		String path1 = ".\\Test2\\HWSA2";

		ImagePlus imp1 = UtilAyv.openImageNoDisplay(path1, false);
		boolean demo = true;
		boolean silent = false;
		int diam = 10;
		int timeout = 2000;
		int[][] pippo = p17rmn_.automaticRoiPreparation3(imp1, diam, silent,
				timeout, demo);

		MyLog.waitHere();

	}

	@Test
	public final void testMainWarpFast() {

		// 04 sep 2013 funziona in automatico

		String path1 = ".\\Test2\\HWSA_testP7";
		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean demo = true;
		boolean test = false;
		boolean fast = true;
		boolean silent = false;
		boolean verbose = false;
		int timeout = 3000;

		ResultsTable rt1 = p17rmn_.mainWarp(path1, autoCalled, step, silent,
				verbose, test, demo, timeout);
		int[][] vetResults = UtilAyv.vectorizeResults2(rt1);
		int[][] vetReference = p17rmn_.referenceSiemens();
		boolean ok = UtilAyv.verifyResults3(vetResults, vetReference);
		assertTrue(ok);
	}

}