package contMensili;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import ij.IJ;
import ij.ImageJ;
import ij.gui.WaitForUserDialog;
import ij.measure.ResultsTable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import utils.MyConst;
import utils.UtilAyv;

public class p13rmn_Test {

	@Before
	public void setUp() throws Exception {
		new ImageJ(ImageJ.NORMAL);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public final void testDecodeTokens() {
		String arg = "1#2#12#14#15";
		int[] expected = { 1, 2, 12, 14, 15 };
		int[] lista = UtilAyv.decodeTokens(arg);
		assertTrue(UtilAyv.compareVectors(expected, lista, ""));
	}

	@Test
	public final void testUiPercCalculation() {
		double max = 1752.0;
		double min = 1425.0;
		double expected = 89.70727101038716;
		double result = p13rmn_.uiPercCalculation(max, min);
		assertEquals(expected, result, 1e-12);
	}

	@Test
	public final void testGhostPercCalculation() {
		double mediaGhost = 25.332278481012658;
		double meanBkg = 24.389240506329113;
		double meanImage = 1947.5417105065385;
		double expected = 0.04842196547555682;
		double result = p13rmn_.ghostPercCalculation(mediaGhost, meanBkg, meanImage);
		assertEquals(expected, result, 1e-12);
	}

	@Test
	public final void testMainUnifor() {
		String path1 = "./Test2/HUSA_001testP3";
		String path2 = "./Test2/HUSA_002testP3";
		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = true;
		boolean verbose = true;
		boolean test = true;
		p13rmn_.prepUnifor(path1, path2, autoArgs, autoCalled, step, verbose, test);
		// new WaitForUserDialog("Do something, then click OK.").show();
		IJ.wait(500);
	}

	@Test
	public final void testMixUniforTestSiemens() {
		String path1 = ".\\Test2\\HUSA_001testP3";
		String path2 = ".\\Test2\\HUSA_002testP3";
		double[] vetReference = new p13rmn_().referenceSiemens();
		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = true;
		UtilAyv.setMeasure(MyConst.HMEAN + MyConst.HSTD_DEV);
		int[] roiData = { MyConst.P3_X_ROI_TESTSIEMENS, MyConst.P3_Y_ROI_TESTSIEMENS, MyConst.P3_DIAM_PHANTOM };
		ResultsTable rt1 = p13rmn_.mainUnifor(path1, path2, roiData, autoArgs, autoCalled, step, verbose, test);
		double[] vetResults = UtilAyv.vectorizeResults(rt1);
		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference, MyConst.P3_vetName);
		assertTrue(ok);
	}

	@Test
	public final void testMixUniforTestGe() {
		String path1 = ".\\Test2\\HUSA2_01testP3";
		String path2 = ".\\Test2\\HUSA2_02testP3";
		double[] vetReference = new p13rmn_().referenceGe();
		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = true;
		UtilAyv.setMeasure(MyConst.HMEAN + MyConst.HSTD_DEV);
		int[] roiData = { MyConst.P3_X_ROI_TESTGE, MyConst.P3_Y_ROI_TESTGE, MyConst.P3_DIAM_PHANTOM };
		ResultsTable rt1 = p13rmn_.mainUnifor(path1, path2, roiData, autoArgs, autoCalled, step, verbose, test);
		double[] vetResults = UtilAyv.vectorizeResults(rt1);
		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference, MyConst.P3_vetName);
		assertTrue(ok);
	}

	@Test
	public final void testMixUniforTestSilent() {

		new p13rmn_().selfTestSilent();

	}

}