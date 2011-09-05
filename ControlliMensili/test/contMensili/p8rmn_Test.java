package contMensili;

import static org.junit.Assert.assertEquals;
import ij.ImagePlus;

import java.awt.Polygon;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import utils.ReadDicom;
import utils.UtilAyv;

public class p8rmn_Test {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public final void testOverlayNumbers() {

		ImagePlus imp1 = UtilAyv.openImageMaximized(".\\Test2\\HT2A_testP8");

		new p8rmn_().overlayNumbers(imp1, true);

		int[] vetX = { 52, 208, 208, 52 };
		int[] vetY = { 61, 61, 215, 215 };
		String DICOM_PIXEL_SPACING = "0028,0030";
		double dimPixel = ReadDicom.readDouble(ReadDicom.readSubstring(
				ReadDicom.readDicomParameter(imp1, DICOM_PIXEL_SPACING), 1));
		Polygon poli1 = UtilAyv.clickSimulation(imp1, vetX, vetY);
		double[] vetResults = new p8rmn_().mainCalculation(poli1, dimPixel);
		double[] vetReference = new p8rmn_().referenceSiemens();
		new p8rmn_().verifyResults(vetResults, vetReference, false);
	}

	@Test
	public final void testSegmentCalculation() {
		double dimPixel = 0.78125;
		double xStart = 52;
		double yStart = 61;
		double xEnd = 208;
		double yEnd = 61;

		double len = p8rmn_.segmentCalculation(xStart, yStart, xEnd, yEnd,
				dimPixel);
		double expected = 121.875;
		assertEquals(expected, len, 1e-25);
	}

	@Test
	public final void testDgpCalculation() {
		double segmCalc = 121.875;
		double PHANTOM_SIDE = 120.0;
		double dgp = p8rmn_.dgpCalculation(segmCalc, PHANTOM_SIDE);
		double expected = 1.5625;
		assertEquals(expected, dgp, 1e-25);
	}

}