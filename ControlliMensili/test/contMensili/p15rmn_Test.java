package contMensili;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Rectangle;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import utils.InputOutput;
import utils.MyConst;
import utils.MyLog;
import utils.MyPlot;
import utils.ReadDicom;
import utils.UtilAyv;

public class p15rmn_Test {

	@Before
	public void setUp() throws Exception {
		new ImageJ(ImageJ.NORMAL);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testVersion() {
		assertFalse(IJ.versionLessThan("1.42p"));
	}

	@Test
	public final void testCalculateMTF() {

		String path1 = "./Test2/004_P14";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		int latoWidth = 130;
		int latoHeight = 150;
		int centerx = 480;
		int centery = 360;

		imp1.setRoi(centerx - latoWidth / 2, centery - latoHeight / 2, latoWidth, latoHeight);
		MyLog.waitHere("posizionare la ROI sullo SLANTED EDGE, in modo che la parte nera sia A SINISTRA e premere OK");

		p15rmn_ p15 = new p15rmn_();

	//	p15.MTF(imp1, null, null);

		MyLog.waitHere();

	}


	@Test
	public final void testPositionSearch1() {

		String path1 = "./Test2/gradi1";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		double minSizeInPixel = 5000;
		double maxSizeInPixel = 100000;
		double minCirc = .3;
		double maxCirc = 1;
		boolean step = true;

		p15rmn_ p15 = new p15rmn_();
		Roi roi14 = p15.positionSearch(imp1, minSizeInPixel, maxSizeInPixel, minCirc, maxCirc, step, false);
		MyLog.waitHere("finito");

	}

	@Test
	public final void testPositionSearch2() {

		String path1 = "./Test2/HRKA";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		double minSizeInPixel = 5000;
		double maxSizeInPixel = 100000;
		double minCirc = .1;
		double maxCirc = 1;
		boolean step = true;

		p15rmn_ p15 = new p15rmn_();
		Roi roi14 = p15.positionSearch(imp1, minSizeInPixel, maxSizeInPixel, minCirc, maxCirc, step, false);
		MyLog.waitHere("FINITO FUORI");

		// ImagePlus imp3 = imp2.crop();
		// imp3.show();

	}

	@Test
	public final void testMainMTF_1() {

		String path1 = "./Test2/HRKA";
		String autoArgs = "0";
		int mode = 2;
		int timeout = 200;
		p15rmn_ p15 = new p15rmn_();
		ResultsTable rt1 = p15.mainMTF(path1, autoArgs, "info10", mode, timeout);
		rt1.show("Results");
		MyLog.waitHere("FINITO FUORI");
	}

	@Test
	public final void testMainMTF_2() {

		String path1 = "./Test2/gradi1";
		String autoArgs = "0";
		int mode = 2;
		int timeout = 200;
		p15rmn_ p15 = new p15rmn_();
		ResultsTable rt1 = p15.mainMTF(path1, autoArgs, "info10", mode, timeout);
		rt1.show("Results");
		MyLog.waitHere("FINITO FUORI");
	}

	@Test
	public final void testMainMTF_3() {

		String path1 = "./Test2/HRKA";
		String autoArgs = "0";
		int mode = 1;
		int timeout = 200;
		p15rmn_ p15 = new p15rmn_();
		ResultsTable rt1 = p15.mainMTF(path1, autoArgs, "info10", mode, timeout);
		rt1.show("Results");
		MyLog.waitHere("FINITO FUORI");
	}
	
	@Test
	public final void testMainMTF_4() {

		String path1 = "./Test2/HRKA3_P14";
		String autoArgs = "0";
		int mode = 1;
		int timeout = 200;
		p15rmn_ p15 = new p15rmn_();
		ResultsTable rt1 = p15.mainMTF(path1, autoArgs, "info10", mode, timeout);
		rt1.show("Results");
		MyLog.waitHere("FINITO FUORI");
	}

	@Test
	public final void testComplete1() {

		String path1 = "./Test2/gradi1";

		p15rmn_ p15 = new p15rmn_();
		String autoArgs = "6";

		int mode = 3;
		int timeout = 200;
		String info= "INFO";

		p15.mainMTF(path1, autoArgs, info, mode, timeout);
		MyLog.waitHere();

	}

}