package contMensili;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Line;
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

public class p16rmn_Test {

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
	public final void testAnalProfFirstSlab() {
		double vetProfile[] = { 12.00024654579077, 31.200641019056,
				144.00295854948925, 31.200641019056 }; // [xStart,yStart,xEnd,yEnd]
		double lato = 0;
		double[] vetTestPositions = { 51.0, 36.0, 52.0, 192.0, }; // [xStart,yStart,xEnd,yEnd]
		String[] list = { "066" };
		String[] path2 = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
		String path1 = path2[0];
		MyLog.waitHere("path1= "+path1);
		ImagePlus imp3 = UtilAyv.openImageMaximized(path1);
		double dimPixel = 0.78125;
		int ra1 = 13;
		imp3.setRoi(new Line((int) vetTestPositions[0],
				(int) vetTestPositions[1], (int) vetTestPositions[2],
				(int) vetTestPositions[3], imp3));
		imp3.updateAndRepaintWindow();
		Roi r1 = imp3.getRoi();
		assert (r1 != null);
		Roi roi = imp3.getRoi();
		Line line2 = (Line) roi;
		lato = line2.getRawLength();
		assertEquals(156.00320509528, lato, 1e-25);
		boolean slab = true;
		boolean invert = false;
		// N.B: mettendo verbose a true si pu� vedere la grafica funzionare
		boolean verbose = false;
		boolean bLabelSx = false;
		double[] dsd1 = new p6rmn_().analProf(imp3, vetTestPositions,
				vetProfile, ra1, slab, invert, verbose, bLabelSx, dimPixel);
		double fwhm = dsd1[0];
		double peak = dsd1[1];
		assertEquals(10.090520971808136, fwhm, 1e-25);
		assertEquals(42.96875, peak, 1e-25);
	}

	@Test
	public final void testAnalProfFirstWedge() {
		double vetProfile[] = { 12.00024654579077, 93.60192305716801,
				144.00295854948925, 93.60192305716801 }; // [xStart,yStart,xEnd,yEnd]
		double lato = 0;
		double[] vetTestPositions = { 51.0, 36.0, 52.0, 192.0 }; // [xStart,yStart,xEnd,yEnd]
		String[] list = { "066.ima" };
		String[] path2 = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
		String path1 = path2[0];
		ImagePlus imp3 = UtilAyv.openImageMaximized(path1);
		double dimPixel = 0.78125;
		int ra1 = 13;
		imp3.setRoi(new Line((int) vetTestPositions[0],
				(int) vetTestPositions[1], (int) vetTestPositions[2],
				(int) vetTestPositions[3], imp3));
		imp3.updateAndRepaintWindow();
		Roi r1 = imp3.getRoi();
		assert (r1 != null);
		Roi roi = imp3.getRoi();
		Line line2 = (Line) roi;
		lato = line2.getRawLength();
		assertEquals(156.00320509528, lato, 1e-25);
		boolean slab = false;
		boolean invert = true;
		// N.B: mettendo verbose a true si pu� vedere la grafica funzionare
		boolean verbose = false;
		boolean bLabelSx = false;
		double[] dsd1 = new p6rmn_().analProf(imp3, vetTestPositions,
				vetProfile, ra1, slab, invert, verbose, bLabelSx, dimPixel);
		double fwhm = dsd1[0];
		double peak = dsd1[1];
		assertEquals(8.090445406547264, fwhm, 1e-25);
		assertEquals(44.53125, peak, 1e-25);
	}

}