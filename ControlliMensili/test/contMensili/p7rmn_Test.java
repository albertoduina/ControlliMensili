package contMensili;

import ij.IJ;
import ij.ImagePlus;

import java.awt.Polygon;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import utils.InputOutput;
import utils.MyConst;
import utils.ReadDicom;
import utils.UtilAyv;

public class p7rmn_Test {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public final void testOverlayRodNumbers() {

		ImagePlus imp1 = UtilAyv.openImageMaximized(".\\Test2\\HWSA_testP7");
		double dimPixel2 = ReadDicom.readDouble(ReadDicom.readSubstring(
				ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));
		double diamRoi1 = (double) MyConst.P7_DIAM_ROI / dimPixel2;
		int diamRoi = (int) diamRoi1;
		boolean circular = true;
		UtilAyv.presetRoi(imp1, diamRoi, 0, 20, circular);
		new p7rmn_().overlayRodNumbers(imp1, diamRoi, true);

//		ButtonMessages.ModalMsg("", "CONTINUA");
		IJ.wait(500);
		int[] vetX = InputOutput.readIntArrayFromFile("./data/vet01.txt");
		int[] vetY = InputOutput.readIntArrayFromFile("./data/vet02.txt");
		Polygon poli1 = UtilAyv.clickSimulation(imp1, vetX, vetY);
		UtilAyv.verifyResults2(poli1.xpoints, poli1.ypoints, vetX, vetY, "della ROD ");
		IJ.wait(500);
		
	}

	@Test
	public final void testSelfTestRoutine() {
		ImagePlus imp1 = UtilAyv.openImageMaximized(".\\Test2\\HWSA_testP7");
		int[] vetX = InputOutput.readIntArrayFromFile("./data/vet01.txt");
		int[] vetY = InputOutput.readIntArrayFromFile("./data/vet02.txt");
		Polygon poli1 = UtilAyv.clickSimulation(imp1, vetX, vetY);
		UtilAyv.verifyResults2(poli1.xpoints, poli1.ypoints, vetX, vetY, "della ROD ");

	}
	
	@Test
	public final void testSelfTestSilent() {
		new p7rmn_().selfTestSilent();
	}
	
	
	
	
	
}