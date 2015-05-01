package contMensili;

import static org.junit.Assert.assertTrue;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import utils.MyConst;
import utils.MyLog;
import utils.UtilAyv;

public class p20rmn_Test {

	@Before
	public void setUp() throws Exception {
		// new ImageJ(ImageJ.NORMAL);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public final void testManualMenuTest() {
		int preset = 1;
		String testDirectory = "";
		boolean testA = true;
		ImagePlus imp11 = p20rmn_.manualMenu(preset, testDirectory, testA);
		UtilAyv.afterWork();
	}

	@Test
	public final void testMsgT1T2Test() {
		boolean T2 = p20rmn_.msgT1T2();
		boolean debug=true;
		int timeout=100;
		
		MyLog.waitHere("T2= ", debug, timeout);


	}

	@Test
	public final void testManualImageSelectionTest() {
		String[] out1 = p20rmn_.manualImageSelection();
		MyLog.logVector(out1, "out1");
		MyLog.waitHere();
	}

	@Test
	public final void testManualRoiPreparationTest() {
		String vetPath[] = { ".\\Test2\\HT1A2_01testP2",
				".\\Test2\\HT1A2_02testP2", ".\\Test2\\HT1A2_03testP2",
				".\\Test2\\HT1A2_04testP2" };
		boolean typeT2 = true;
		ImagePlus imp1 = p20rmn_.imaPreparation(vetPath, typeT2);
		UtilAyv.showImageMaximized(imp1);
		Roi[] pippo = p20rmn_.manualRoiPreparation(imp1);
//		MyLog.waitHere();
	}

	@Test
	public final void testAutomaticRoiPreparationTest() {
		String vetPath[] = { ".\\Test2\\HT1A2_01testP2",
				".\\Test2\\HT1A2_02testP2", ".\\Test2\\HT1A2_03testP2",
				".\\Test2\\HT1A2_04testP2" };
		boolean typeT2 = true;
		ImagePlus imp1 = p20rmn_.imaPreparation(vetPath, typeT2);
		UtilAyv.showImageMaximized(imp1);
		Roi[] pippo = p20rmn_.automaticRoiPreparation(imp1,
				MyConst.P20_X_ROI_TESTGE, MyConst.P20_Y_ROI_TESTGE,
				MyConst.P20_DIAM_ROI);
//		MyLog.waitHere();
	}

	@Test
	public final void testAutomaticRoiPreparationTest2() {

		String path1 = ".\\Test2\\B003_testP2";
		ImagePlus imp1 = UtilAyv.openImageNoDisplay(path1, false);
		boolean demo = true;
		int diam = 20;
		int timeout = 2000;
		Roi[] pippo = p20rmn_.automaticRoiPreparation2(imp1, diam, timeout,
				demo);

		String[] expected = { "Roi[Oval, x=97, y=39, width=20, height=20]",
				"Roi[Oval, x=148, y=41, width=20, height=20]",
				"Roi[Oval, x=42, y=88, width=20, height=20]",
				"Roi[Oval, x=94, y=90, width=20, height=20]",
				"Roi[Oval, x=144, y=92, width=20, height=20]",
				"Roi[Oval, x=196, y=95, width=20, height=20]",
				"Roi[Oval, x=40, y=139, width=20, height=20]",
				"Roi[Oval, x=91, y=143, width=20, height=20]",
				"Roi[Oval, x=142, y=144, width=20, height=20]",
				"Roi[Oval, x=194, y=147, width=20, height=20]",
				"Roi[Oval, x=88, y=194, width=20, height=20]",
				"Roi[Oval, x=139, y=197, width=20, height=20]" };

		boolean result = true;
		boolean ok = false;
		for (int i1 = 0; i1 < pippo.length; i1++) {
			String seconda = pippo[i1].toString();
			String prima = expected[i1];
			ok = prima.equals(seconda);
			if (!ok)
				result = false;
		}
		assertTrue(result);
	}

	// @Test
	// public final void testAutomaticRoiPreparationTest3() {
	//
	// String path1 = ".\\Test2\\HWSA_testP7";
	// ImagePlus imp1 = UtilAyv.openImageNoDisplay(path1, false);
	// boolean demo = false;
	// int diam = 10;
	// int timeout = 2000;
	// Roi[] pippo = p20rmn_.automaticRoiPreparation3(imp1, diam, timeout,
	// demo);
	//
	// // for (int i1 = 0; i1 < pippo.length; i1++) {
	// // IJ.log("" + pippo[i1].toString());
	// // }
	// //
	// // MyLog.waitHere();
	//
	// String[] expected = { "Roi[Oval, x=251, y=81, width=10, height=10]",
	// "Roi[Oval, x=247, y=100, width=10, height=10]",
	// "Roi[Oval, x=378, y=133, width=10, height=10]",
	// "Roi[Oval, x=118, y=134, width=10, height=10]",
	// "Roi[Oval, x=121, y=151, width=10, height=10]",
	// "Roi[Oval, x=375, y=152, width=10, height=10]",
	// "Roi[Oval, x=249, y=171, width=10, height=10]",
	// "Roi[Oval, x=247, y=191, width=10, height=10]",
	// "Roi[Oval, x=183, y=197, width=10, height=10]",
	// "Roi[Oval, x=312, y=198, width=10, height=10]",
	// "Roi[Oval, x=183, y=217, width=10, height=10]",
	// "Roi[Oval, x=313, y=217, width=10, height=10]",
	// "Roi[Oval, x=85, y=262, width=10, height=10]",
	// "Roi[Oval, x=165, y=262, width=10, height=10]",
	// "Roi[Oval, x=329, y=262, width=10, height=10]",
	// "Roi[Oval, x=411, y=262, width=10, height=10]",
	// "Roi[Oval, x=83, y=281, width=10, height=10]",
	// "Roi[Oval, x=165, y=281, width=10, height=10]",
	// "Roi[Oval, x=330, y=281, width=10, height=10]",
	// "Roi[Oval, x=411, y=281, width=10, height=10]",
	// "Roi[Oval, x=310, y=326, width=10, height=10]",
	// "Roi[Oval, x=181, y=327, width=10, height=10]",
	// "Roi[Oval, x=183, y=346, width=10, height=10]",
	// "Roi[Oval, x=312, y=346, width=10, height=10]",
	// "Roi[Oval, x=247, y=353, width=10, height=10]",
	// "Roi[Oval, x=246, y=372, width=10, height=10]",
	// "Roi[Oval, x=119, y=391, width=10, height=10]",
	// "Roi[Oval, x=375, y=392, width=10, height=10]",
	// "Roi[Oval, x=375, y=409, width=10, height=10]",
	// "Roi[Oval, x=119, y=410, width=10, height=10]",
	// "Roi[Oval, x=246, y=443, width=10, height=10]",
	// "Roi[Oval, x=247, y=462, width=10, height=10]",
	// "Roi[Oval, x=251, y=242, width=10, height=10]",
	// "Roi[Oval, x=218, y=273, width=10, height=10]",
	// "Roi[Oval, x=283, y=276, width=10, height=10]",
	// "Roi[Oval, x=249, y=307, width=10, height=10]" };
	//
	// boolean result = true;
	// boolean ok = false;
	// for (int i1 = 0; i1 < pippo.length; i1++) {
	// String seconda = pippo[i1].toString();
	// String prima = expected[i1];
	// ok = prima.equals(seconda);
	// if (!ok)
	// result = false;
	// }
	// assertTrue(result);
	// }

	@Test
	public final void testMapPreparationTest() {
		String vetPath[] = { ".\\Test2\\HT1A2_01testP2",
				".\\Test2\\HT1A2_02testP2", ".\\Test2\\HT1A2_03testP2",
				".\\Test2\\HT1A2_04testP2" };
		boolean typeT2 = true;
		ImagePlus imp1 = p20rmn_.imaPreparation(vetPath, typeT2);
		Roi[] vetRoi = p20rmn_.automaticRoiPreparation(imp1,
				MyConst.P20_X_ROI_TESTGE, MyConst.P20_Y_ROI_TESTGE,
				MyConst.P20_DIAM_ROI);
		int[] bMap = p20rmn_.mapPreparation(imp1, vetRoi);
		// MyLog.waitHere();
	}

	@Test
	public final void testFilterPreparationTest() {
		String vetPath[] = { ".\\Test2\\HT1A2_01testP2",
				".\\Test2\\HT1A2_02testP2", ".\\Test2\\HT1A2_03testP2",
				".\\Test2\\HT1A2_04testP2" };
		boolean typeT2 = true;
		boolean bstep = false;
		ImagePlus imp1 = p20rmn_.imaPreparation(vetPath, typeT2);
		Roi[] vetRoi = p20rmn_.automaticRoiPreparation(imp1,
				MyConst.P20_X_ROI_TESTGE, MyConst.P20_Y_ROI_TESTGE,
				MyConst.P20_DIAM_ROI);
		// int[] bMap = p20rmn_.mapPreparation(imp1, vetRoi);
		double filter = p20rmn_.filterPreparation(imp1, vetRoi, bstep);
		// MyLog.waitHere("filter  aaaaa  = " + filter);
	}

	@Test
	public final void testTestSilent() {
		new p20rmn_().selfTestSilent();
	}

}